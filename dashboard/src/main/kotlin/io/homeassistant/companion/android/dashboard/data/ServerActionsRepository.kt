package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.entity.formatIcuMessage
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** What the dashboards ask of the active server when the user acts: service calls and what they need. */
class ServerActionsRepository @Inject constructor(private val serverManager: ServerManager) {
    /**
     * Call `[domain].[service]` (`call_service`), as the frontend's `callService` does.
     *
     * @return `null` when it succeeded, otherwise why it failed
     */
    suspend fun callService(domain: String, service: String, data: JsonObject?, target: JsonObject?): LoadError? {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return LoadError.NoServer
        val message = buildMap<String, Any?> {
            put("domain", domain)
            put("service", service)
            data?.let { put("service_data", it) }
            target?.let { put("target", it) }
        }
        return (webSocket.request("call_service", message) as? Fetched.Failure)?.error
    }

    /**
     * The integration's message for [translation] in [language], as the frontend loads its `exceptions` translations
     * to show a failed action; `null` when it has none.
     */
    suspend fun errorMessage(translation: ErrorTranslation, language: String): Fetched<String?> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        val key = "component.${translation.domain}.exceptions.${translation.key}.message"
        return webSocket.request(
            "frontend/get_translations",
            mapOf("language" to language, "category" to "exceptions", "integration" to listOf(translation.domain)),
        ).expect<JsonObject>().map { result ->
            result.obj("resources")?.string(key)?.let { formatIcuMessage(it, translation.placeholders) }
        }
    }

    /**
     * The default code stored for [entityId] (`options.[optionsDomain].default_code` of its registry entry), as
     * the frontend reads it before asking for a lock or alarm code; `null` when there is none.
     */
    suspend fun defaultCode(entityId: String, optionsDomain: String): Fetched<String?> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("config/entity_registry/get", mapOf("entity_id" to entityId)).expect<JsonObject>()
            .map { it.obj("options")?.obj(optionsDomain)?.string("default_code")?.ifEmpty { null } }
    }
}
