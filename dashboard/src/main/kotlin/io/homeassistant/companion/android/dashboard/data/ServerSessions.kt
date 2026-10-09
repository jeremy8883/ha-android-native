package io.homeassistant.companion.android.dashboard.data

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * The active server's sessions for loading dashboard data, shared by the repositories of one screen so that the
 * user's retries reach all of its data.
 */
@ViewModelScoped
class ServerSessions @Inject constructor(
    private val serverManager: ServerManager,
    private val loadedData: LoadedData,
) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Load again everything that failed or is collected, now rather than at the next retry. */
    fun retry() {
        retries.tryEmit(Unit)
    }

    /** [block]'s data for the active server, or failed when there is none. */
    internal fun <T> withServer(block: (ServerSession) -> Flow<Loadable<T>>): Flow<Loadable<T>> = flow {
        val serverId = serverManager.getServer()?.id
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (serverId == null || webSocket == null) {
            emit(Loadable.Failed(LoadError.NoServer))
        } else {
            emitAll(block(ServerSession(serverId, webSocket, loadedData, retries)))
        }
    }
}

/** What loading a server's data needs: its connection, where values are kept, and the user's retries. */
internal class ServerSession(
    val serverId: Int,
    val webSocket: WebSocketRepository,
    private val loadedData: LoadedData,
    private val retries: Flow<Unit>,
) {
    val connection = webSocket.connectionStatus()

    /**
     * [name], kept up to date (see [KeptData.fetched]) as the raw responses [fetch] gathers and read with [parse], both
     * when loaded and when read back from the cache. Loaded again on each [refreshes] emission and on the user's
     * retries.
     */
    fun <P> parsed(
        name: String,
        refreshes: Flow<Any?> = emptyFlow(),
        parse: (JsonObject) -> Fetched<P>,
        fetch: suspend () -> Fetched<JsonObject>,
    ): Flow<Loadable<P>> = KeptData(name, loadedData.keeper(serverId, name, ParsedCodec(parse)), connection)
        .fetched(merge(refreshes.map {}, retries)) {
            fetch().flatMap { raw -> parse(raw).map { Parsed(raw, it) } }
        }
        .map { loadable -> loadable.map { it.value } }

    suspend fun request(type: String, data: Map<String, Any?> = emptyMap()): Fetched<JsonElement?> =
        webSocket.request(type, data)

    suspend fun subscribe(type: String): Fetched<Flow<JsonElement>> =
        webSocket.subscribeRaw(type)?.let { Fetched.Success(it) } ?: Fetched.Failure(LoadError.NoResponse)

    /**
     * An emission for each [eventType] event, subscribing again after a failure so no change goes unnoticed for
     * long; the data it refreshes is loaded again on reconnection anyway.
     */
    fun events(eventType: String): Flow<JsonElement> = flow {
        var failures = 0
        while (true) {
            val events = webSocket.subscribeRaw(SUBSCRIBE_EVENTS, mapOf("event_type" to eventType))
            if (events != null) {
                failures = 0
                emitAll(events)
            }
            Timber.w("Subscription to $eventType events failed or ended, subscribing again")
            delay(RetryDelays.DEFAULT.after(failures++))
        }
    }
}

/** The outcome of the [type] command: its result (which may be `null`), or why there is none. */
internal suspend fun WebSocketRepository.request(
    type: String,
    data: Map<String, Any?> = emptyMap(),
): Fetched<JsonElement?> {
    val response = sendRawMessage(mapOf("type" to type) + data)
    return when {
        response == null -> Fetched.Failure(LoadError.NoResponse)
        response.success -> Fetched.Success(response.result)
        else -> (response.error as? JsonObject).let { error ->
            Fetched.Failure(
                LoadError.Server(
                    code = error?.string("code"),
                    message = error?.string("message"),
                    translation = error?.let(::errorTranslation),
                ),
            )
        }
    }
}

private fun errorTranslation(error: JsonObject): ErrorTranslation? {
    val domain = error.string("translation_domain")
    val key = error.string("translation_key")
    if (domain == null || key == null) return null
    val placeholders = error.obj("translation_placeholders")
        ?.mapNotNull { (name, value) -> value.stringOrNull?.let { name to it } }.orEmpty().toMap()
    return ErrorTranslation(domain, key, placeholders)
}

/** The result as a [J], or failed when it is something else. */
internal inline fun <reified J : JsonElement> Fetched<JsonElement?>.expect(): Fetched<J> = flatMap { result ->
    (result as? J)?.let { Fetched.Success(it) }
        ?: Fetched.Failure(LoadError.UnexpectedResponse(J::class.simpleName.orEmpty()))
}

/** A refusal by the server as no result, for data upstream treats as absent when refused. */
internal fun Fetched<JsonElement?>.absentWhenRefused(): Fetched<JsonElement?> =
    if ((this as? Fetched.Failure)?.error is LoadError.Server) Fetched.Success(null) else this

private const val SUBSCRIBE_EVENTS = "subscribe_events"
