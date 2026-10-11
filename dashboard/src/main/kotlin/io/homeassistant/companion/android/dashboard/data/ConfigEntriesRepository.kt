package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** The server's config entries (`config_entries/get`), as the target picker loads them to name devices' integrations. */
class ConfigEntriesRepository @Inject constructor(private val sessions: ServerSessions) {

    /** Each config entry's integration, by entry id, loaded once; a failure is logged and shown as such. */
    fun domains(): Flow<Loadable<Map<String, String>>> = sessions.withServer { session ->
        flow {
            val result = session.request(CONFIG_ENTRIES).expect<JsonArray>().map { entries ->
                entries.filterIsInstance<JsonObject>().mapNotNull { entry ->
                    val id = entry.string("entry_id") ?: return@mapNotNull null
                    entry.string("domain")?.let { id to it }
                }.toMap()
            }
            when (result) {
                is Fetched.Success -> emit(Loadable.Ready(result.value))
                is Fetched.Failure -> {
                    Timber.w("Couldn't load the config entries: ${result.error}")
                    emit(Loadable.Failed(result.error))
                }
            }
        }
    }

    private companion object {
        const val CONFIG_ENTRIES = "config_entries/get"
    }
}
