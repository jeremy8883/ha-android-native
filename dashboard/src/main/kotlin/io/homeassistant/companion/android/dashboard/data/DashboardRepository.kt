package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.RawWebSocketResponse
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.model.DashboardConfig
import io.homeassistant.companion.android.dashboard.model.DashboardInfo
import io.homeassistant.companion.android.dashboard.model.ERROR_CONFIG_NOT_FOUND
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.parseDashboards
import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** Result of loading a dashboard's stored config. */
sealed interface DashboardConfigResult {
    data class Loaded(val config: DashboardConfig) : DashboardConfigResult

    /** The dashboard exists but has no stored config, so the frontend would generate one. */
    data object NotFound : DashboardConfigResult

    data class Error(val message: String?) : DashboardConfigResult
}

/**
 * Raw dashboard data from the active server. Every Flow is cold: the underlying WebSocket subscriptions
 * live only while collected, and are restored by `:common` after a reconnection.
 */
class DashboardRepository @Inject constructor(private val serverManager: ServerManager) {

    /**
     * All entity states, kept up to date through `subscribe_entities`. Emits once the initial snapshot has
     * arrived, then after every change. Completes without emitting when no connection can be made.
     */
    fun entityStates(): Flow<EntityStates> = flow {
        val events = serverManager.webSocketRepositoryOrNull()?.subscribeRaw(SUBSCRIBE_ENTITIES) ?: return@flow
        emitAll(
            events
                .filter { it is JsonObject }
                .runningFold<JsonElement, EntityStates>(emptyMap()) { states, event ->
                    applyEntityEvent(states, event as JsonObject)
                }
                // Skip the empty seed so consumers never mistake it for "no entities"
                .drop(1),
        )
    }

    /** The dashboards stored on the server, without the default dashboard. */
    suspend fun dashboards(): List<DashboardInfo> {
        val response = serverManager.webSocketRepositoryOrNull()
            ?.sendRawMessage(mapOf("type" to "lovelace/dashboards/list"))
        val result = response?.takeIf { it.success }?.result as? JsonArray ?: return emptyList()
        return parseDashboards(result)
    }

    /**
     * The stored config of the dashboard at [urlPath] (`null` for the default dashboard), fetched again
     * whenever the server reports a `lovelace_updated` event for it.
     */
    fun dashboardConfig(urlPath: String?): Flow<DashboardConfigResult> = channelFlow {
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (webSocket == null) {
            send(DashboardConfigResult.Error(null))
            return@channelFlow
        }
        suspend fun fetch() {
            val response = webSocket.sendRawMessage(
                mapOf("type" to "lovelace/config", "url_path" to urlPath, "force" to false),
            )
            send(response.toConfigResult())
        }

        launch {
            webSocket.subscribeRaw(SUBSCRIBE_EVENTS, mapOf("event_type" to EVENT_LOVELACE_UPDATED))
                ?.filter { it.updatedUrlPath() == urlPath }
                ?.collect {
                    Timber.d("Dashboard config updated, refetching")
                    fetch()
                }
        }
        fetch()
        awaitClose()
    }
}

private fun RawWebSocketResponse?.toConfigResult(): DashboardConfigResult {
    if (this == null) return DashboardConfigResult.Error(null)
    if (success) {
        val config = result as? JsonObject ?: return DashboardConfigResult.Error(null)
        return DashboardConfigResult.Loaded(DashboardConfig(config))
    }
    val error = error as? JsonObject
    return if (error?.string("code") == ERROR_CONFIG_NOT_FOUND) {
        DashboardConfigResult.NotFound
    } else {
        DashboardConfigResult.Error(error?.string("message"))
    }
}

/** The `url_path` of a `lovelace_updated` event; `null` for the default dashboard. */
private fun JsonElement.updatedUrlPath(): String? = (this as? JsonObject)?.obj("data")?.string("url_path")

private const val SUBSCRIBE_ENTITIES = "subscribe_entities"
private const val SUBSCRIBE_EVENTS = "subscribe_events"
private const val EVENT_LOVELACE_UPDATED = "lovelace_updated"
