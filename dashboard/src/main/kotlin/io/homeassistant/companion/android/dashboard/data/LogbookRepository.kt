package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.logbook.LogbookEntry
import io.homeassistant.companion.android.dashboard.logbook.TRACE_CONTEXTS_COMMAND
import io.homeassistant.companion.android.dashboard.logbook.TraceContext
import io.homeassistant.companion.android.dashboard.logbook.USERS_COMMAND
import io.homeassistant.companion.android.dashboard.logbook.logbookPurgeBefore
import io.homeassistant.companion.android.dashboard.logbook.logbookStreamCommand
import io.homeassistant.companion.android.dashboard.logbook.parseLogbookEvents
import io.homeassistant.companion.android.dashboard.logbook.parseTraceContexts
import io.homeassistant.companion.android.dashboard.logbook.withStreamEvents
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * An entity's recent logbook, as the more-info dialog's logbook section loads it: the logbook stream, with the users
 * and the automation traces its rows name and link to. Kept in memory only, while shown.
 */
class LogbookRepository @Inject constructor(private val sessions: ServerSessions, private val clock: Clock) {

    /**
     * [entityId]'s logbook entries of the last day, newest first, kept up to date: each message's entries are added,
     * and entries that left the day dropped as it goes. After a reconnection it starts again from the stream's first
     * message, so nothing is listed twice. Port of `ha-logbook`'s subscription.
     */
    fun entries(entityId: String): Flow<Loadable<List<LogbookEntry>>> = sessions.withServer { session ->
        val description = "logbook of $entityId"
        KeptData<List<LogbookEntry>>(description, NotKept(), session.connection).subscribed<JsonElement>(
            subscribe = {
                val command = logbookStreamCommand(entityId, clock.now().toJavaInstant())
                session.subscribe(command.type, command.params)
            },
            reduce = { current, event ->
                (event as? JsonObject)?.let(::parseLogbookEvents)?.let { events ->
                    val purgeBefore = logbookPurgeBefore(clock.now().toJavaInstant())
                    // A stream started again after a reconnection asked for the day before its first start
                    current.withStreamEvents(events, purgeBefore).filter { it.whenSeconds > purgeBefore }
                } ?: null.also { Timber.w("Ignoring an unexpected $description message") }
            },
        )
    }

    /** The users (`config/auth/list`), which only admins may list, to name who caused entries. */
    fun users(): Flow<Loadable<JsonArray>> = once("users") { request(USERS_COMMAND).expect<JsonArray>() }

    /** The automation and script runs with a trace, which only admins may list, for the runs' trace links. */
    fun traceContexts(): Flow<Loadable<Map<String, TraceContext>>> = once("trace contexts") {
        request(TRACE_CONTEXTS_COMMAND).expect<JsonObject>().map(::parseTraceContexts)
    }

    /** [fetch]'s result, loaded once; a failure is logged and shown as such. */
    private fun <T> once(description: String, fetch: suspend ServerSession.() -> Fetched<T>): Flow<Loadable<T>> =
        sessions.withServer { session ->
            flow {
                when (val result = session.fetch()) {
                    is Fetched.Success -> emit(Loadable.Ready(result.value))
                    is Fetched.Failure -> {
                        Timber.w("Couldn't load the $description for the logbook: ${result.error}")
                        emit(Loadable.Failed(result.error))
                    }
                }
            }
        }

    private suspend fun ServerSession.request(command: WsCommand): Fetched<JsonElement?> =
        request(command.type, command.params)
}
