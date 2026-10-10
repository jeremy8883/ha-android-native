package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.model.obj
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * An entity's registry entry with its options (`config/entity_registry/get`), as the more-info dialog loads it for
 * the details' favourites, and the updates that change them.
 */
class EntityEntryRepository @Inject constructor(
    private val sessions: ServerSessions,
    private val serverManager: ServerManager,
) {
    /**
     * [entityId]'s registry entry, `null` when it has none: the server refuses entities without a unique ID, which
     * upstream treats as having no entry. Any other failure shows as such.
     */
    fun entry(entityId: String): Flow<Loadable<JsonObject?>> = sessions.withServer { session ->
        flow {
            val result = session.request(GET, mapOf("entity_id" to entityId)).absentWhenRefused().flatMap { entry ->
                when (entry) {
                    null -> Fetched.Success(null)
                    is JsonObject -> Fetched.Success(entry)
                    else -> Fetched.Failure(LoadError.UnexpectedResponse(GET))
                }
            }
            when (result) {
                is Fetched.Success -> emit(Loadable.Ready(result.value))
                is Fetched.Failure -> {
                    Timber.w("Couldn't load the registry entry of $entityId: ${result.error}")
                    emit(Loadable.Failed(result.error))
                }
            }
        }
    }

    /** Send [command], an entity registry update; the updated entry, or why it failed. */
    suspend fun update(command: WsCommand): Fetched<JsonObject> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request(command.type, command.params).expect<JsonObject>().flatMap { result ->
            result.obj("entity_entry")?.let { Fetched.Success(it) }
                ?: Fetched.Failure(LoadError.UnexpectedResponse(command.type))
        }
    }
}

private const val GET = "config/entity_registry/get"
