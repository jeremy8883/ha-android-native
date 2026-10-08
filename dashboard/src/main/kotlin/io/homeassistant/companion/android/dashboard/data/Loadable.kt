package io.homeassistant.companion.android.dashboard.data

/**
 * Server data as the screen sees it. "Not loaded yet" and "failed" are kept apart from a loaded value, so a failure is
 * never shown as empty data.
 */
sealed interface Loadable<out T> {
    /** Nothing loaded yet, and no failure so far. */
    data object Loading : Loadable<Nothing>

    /**
     * A value is loaded.
     *
     * @property refreshing whether it is being loaded again, for example after a reconnection
     * @property refreshError why the last attempt to load it again failed, while [value] is kept; `null` once one
     * succeeds
     */
    data class Ready<out T>(val value: T, val refreshing: Boolean = false, val refreshError: LoadError? = null) :
        Loadable<T>

    /** Nothing could be loaded. Attempts continue (see [LoadError.retries]). */
    data class Failed(val error: LoadError) : Loadable<Nothing>
}

/** The loaded value, or `null` while loading or after a failure. */
val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value

/** This, with its value (if any) transformed by [transform]. */
inline fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    Loadable.Loading -> Loadable.Loading
    is Loadable.Failed -> this
    is Loadable.Ready -> Loadable.Ready(transform(value), refreshing, refreshError)
}

/**
 * [a] and [b] as one: failed when either failed, loading when either is still loading, otherwise ready with their
 * values given to [transform], refreshing when either is, and with the first refresh error.
 */
fun <A, B, R> combineLoadables(a: Loadable<A>, b: Loadable<B>, transform: (A, B) -> R): Loadable<R> =
    combineAll(a, b) { values ->
        @Suppress("UNCHECKED_CAST")
        transform(values[0] as A, values[1] as B)
    }

/** [a], [b] and [c] as one, as the two-argument `combineLoadables` does. */
fun <A, B, C, R> combineLoadables(
    a: Loadable<A>,
    b: Loadable<B>,
    c: Loadable<C>,
    transform: (A, B, C) -> R,
): Loadable<R> = combineAll(a, b, c) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(values[0] as A, values[1] as B, values[2] as C)
}

private fun <R> combineAll(vararg loadables: Loadable<*>, transform: (List<Any?>) -> R): Loadable<R> {
    val failed = loadables.firstNotNullOfOrNull { it as? Loadable.Failed }
    val ready = loadables.filterIsInstance<Loadable.Ready<*>>()
    return when {
        failed != null -> failed
        ready.size < loadables.size -> Loadable.Loading
        else -> Loadable.Ready(
            value = transform(ready.map { it.value }),
            refreshing = ready.any { it.refreshing },
            refreshError = ready.firstNotNullOfOrNull { it.refreshError },
        )
    }
}

/** Why server data couldn't be loaded. */
sealed interface LoadError {
    /** Whether trying again on its own may succeed; a server's refusal stays until something changes. */
    val retries: Boolean

    /** There is no server to load from. */
    data object NoServer : LoadError {
        override val retries = false
    }

    /** The request couldn't be sent or got no answer, for example while the connection is down. */
    data object NoResponse : LoadError {
        override val retries = true
    }

    /** The server answered with an error. */
    data class Server(val code: String?, val message: String?) : LoadError {
        override val retries = false
    }

    /** The server's answer is not what [command] returns. */
    data class UnexpectedResponse(val command: String) : LoadError {
        override val retries = false
    }
}

/** The outcome of one request. */
sealed interface Fetched<out T> {
    data class Success<out T>(val value: T) : Fetched<T>

    data class Failure(val error: LoadError) : Fetched<Nothing>
}

/** This, with its value (if any) transformed by [transform]. */
inline fun <T, R> Fetched<T>.map(transform: (T) -> R): Fetched<R> = when (this) {
    is Fetched.Success -> Fetched.Success(transform(value))
    is Fetched.Failure -> this
}

/** This, with its value (if any) replaced by [transform]'s outcome. */
inline fun <T, R> Fetched<T>.flatMap(transform: (T) -> Fetched<R>): Fetched<R> = when (this) {
    is Fetched.Success -> transform(value)
    is Fetched.Failure -> this
}
