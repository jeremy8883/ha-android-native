package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.websocket.WebSocketConnectionStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Where a loaded value is kept between collections, so that loading again (after the screen was left, or the app was
 * in the background) starts from it instead of from nothing.
 */
interface ValueKeeper<T> {
    /** The value kept, or `null` when there is none. */
    suspend fun get(): Kept<T>?

    fun put(value: T)
}

/** Where a value that is not kept beyond its collection is "kept": nowhere. */
internal class NotKept<T> : ValueKeeper<T> {
    override suspend fun get(): Kept<T>? = null

    override fun put(value: T) = Unit
}

/** A kept value, which may itself be `null` when that is what was loaded, and when it was kept. */
data class Kept<out T>(val value: T, val at: Instant)

/** How long to wait before the next attempt after [attempt] consecutive failures. */
fun interface RetryDelays {
    fun after(attempt: Int): Duration

    companion object {
        /** Doubling from a second, up to [MAX_RETRY_DELAY]. */
        val DEFAULT = RetryDelays { attempt ->
            (INITIAL_RETRY_DELAY * (1 shl attempt.coerceAtMost(MAX_DOUBLINGS))).coerceAtMost(MAX_RETRY_DELAY)
        }
    }
}

/**
 * A piece of server data kept up to date, starting from the value [keeper] holds (shown as refreshing until it is
 * loaded again). A failed attempt never replaces a loaded value: it is kept, with the error.
 *
 * @param description what is loaded, for logs
 * @param connection the connection's status, `null` when there is no connection to watch
 */
class KeptData<T>(
    private val description: String,
    private val keeper: ValueKeeper<T>,
    private val connection: StateFlow<WebSocketConnectionStatus>?,
    private val retryDelays: RetryDelays = RetryDelays.DEFAULT,
) {
    /**
     * The value [fetch] loads: loaded when collected, again on every [refreshes] emission and after every
     * reconnection, and again after a failure that [LoadError.retries].
     */
    fun fetched(refreshes: Flow<Unit> = emptyFlow(), fetch: suspend () -> Fetched<T>): Flow<Loadable<T>> = channelFlow {
        val shown = Shown<T>(::send).apply { show(initialState()) }
        // Requests carry the connection they were made for, so a reconnection already loaded on is skipped
        val requests = Channel<Int?>(Channel.UNLIMITED)
        requests.send(null)
        launch { merge(refreshes.map { null }, connection?.reconnections() ?: emptyFlow()).collect(requests::send) }
        var loadedOn: Int? = null
        var failures = 0
        var retry: Job? = null
        for (reconnection in requests) {
            if (reconnection != null && loadedOn != null && reconnection <= loadedOn) continue
            retry?.cancel()
            shown.markRefreshing()
            when (val result = fetch()) {
                is Fetched.Success -> {
                    failures = 0
                    loadedOn = connection?.value?.connections
                    keeper.put(result.value)
                    shown.show(Loadable.Ready(result.value))
                }
                is Fetched.Failure -> {
                    Timber.w("Failed to load $description: ${result.error}")
                    if (result.error.retries) {
                        val wait = retryDelays.after(failures++)
                        retry = launch {
                            delay(wait)
                            requests.send(null)
                        }
                    }
                    shown.show(shown.state.failedWith(result.error))
                }
            }
        }
    }

    /**
     * The value built from a subscription's events by [reduce]: subscribed when collected, and again after a
     * failure. The first event of a subscription, and the first after each reconnection (`:common` subscribes again
     * on its own), is the server's full snapshot, so [reduce] gets `null` as the current value for it. [reduce]
     * returns `null` for an event it can't apply, which is then ignored.
     */
    fun <E> subscribed(
        subscribe: suspend () -> Fetched<Flow<E>>,
        reduce: (current: T?, event: E) -> T?,
    ): Flow<Loadable<T>> = channelFlow {
        val shown = Shown<T>(::send).apply { show(initialState()) }
        var failures = 0
        while (true) {
            val error = when (val subscription = subscribe()) {
                is Fetched.Failure -> subscription.error
                is Fetched.Success -> {
                    if (follow(subscription.value, reduce, shown)) failures = 0
                    Timber.w("Subscription to $description ended")
                    LoadError.NoResponse
                }
            }
            Timber.w("Failed to subscribe to $description: $error")
            shown.show(shown.state.failedWith(error))
            if (error.retries) {
                delay(retryDelays.after(failures++))
            } else {
                // Subscribing again would be refused the same way until the connection changes
                connection?.reconnections()?.first() ?: awaitCancellation()
            }
        }
    }

    /**
     * Apply [events] to the shown value until they end, handling reconnections in the same queue so they keep their
     * order. @return whether any event was applied
     */
    private suspend fun <E> follow(events: Flow<E>, reduce: (T?, E) -> T?, shown: Shown<T>): Boolean = coroutineScope {
        val inputs = Channel<SubscriptionInput<E>>(Channel.UNLIMITED)
        val reconnections =
            launch { connection?.reconnections()?.collect { inputs.send(SubscriptionInput.Reconnected) } }
        launch {
            events.collect { inputs.send(SubscriptionInput.Event(it)) }
            reconnections.cancel()
            inputs.close()
        }
        var snapshotOf: Int? = null
        var current: T? = null
        // Messages that couldn't start the value while its first one was due, in a row
        var unusable = 0
        for (input in inputs) {
            when (input) {
                is SubscriptionInput.Event -> {
                    val connectionNumber = connection?.value?.connections
                    val isSnapshot = current == null || snapshotOf != connectionNumber
                    val reduced = reduce(if (isSnapshot) null else current, input.event)
                    if (reduced != null) {
                        unusable = 0
                        snapshotOf = connectionNumber
                        current = reduced
                        keeper.put(reduced)
                        shown.show(Loadable.Ready(reduced))
                    } else if (isSnapshot && ++unusable >= MAX_UNUSABLE_FIRST_MESSAGES) {
                        // A subscription that joined late never sends its first message: start a new one
                        Timber.w("No usable first message from $description, subscribing again")
                        break
                    }
                }
                // The snapshot of the new connection is on its way
                SubscriptionInput.Reconnected -> shown.markRefreshing()
            }
        }
        coroutineContext.cancelChildren()
        current != null
    }

    private suspend fun initialState(): Loadable<T> =
        keeper.get()?.let { Loadable.Ready(it.value, refreshing = true, keptAt = it.at) } ?: Loadable.Loading
}

/** The state last sent through [send]. */
private class Shown<T>(private val send: suspend (Loadable<T>) -> Unit) {
    var state: Loadable<T> = Loadable.Loading
        private set

    suspend fun show(newState: Loadable<T>) {
        state = newState
        send(newState)
    }

    /** Show a loaded value as being loaded again. */
    suspend fun markRefreshing() {
        val ready = state as? Loadable.Ready ?: return
        if (!ready.refreshing) show(ready.copy(refreshing = true))
    }
}

/** This after an attempt failed with [error]: a loaded value stays, with the error. */
private fun <T> Loadable<T>.failedWith(error: LoadError): Loadable<T> =
    (this as? Loadable.Ready)?.copy(refreshing = false, refreshError = error) ?: Loadable.Failed(error)

private sealed interface SubscriptionInput<out E> {
    data class Event<E>(val event: E) : SubscriptionInput<E>

    data object Reconnected : SubscriptionInput<Nothing>
}

/** The number of each connection established after this is collected. */
private fun StateFlow<WebSocketConnectionStatus>.reconnections(): Flow<Int> =
    map { it.connections }.distinctUntilChanged().drop(1)

private val INITIAL_RETRY_DELAY = 1.seconds
private val MAX_RETRY_DELAY = 30.seconds
private const val MAX_DOUBLINGS = 5

/** How many messages a subscription may send before a usable first one, before it is started again. */
private const val MAX_UNUSABLE_FIRST_MESSAGES = 3
