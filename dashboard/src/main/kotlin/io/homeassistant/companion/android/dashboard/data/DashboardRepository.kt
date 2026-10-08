package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.RawWebSocketResponse
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.model.DashboardConfig
import io.homeassistant.companion.android.dashboard.model.DashboardInfo
import io.homeassistant.companion.android.dashboard.model.ERROR_CONFIG_NOT_FOUND
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.parseDashboards
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
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

    /**
     * The entity, device, area and floor registries, fetched again (debounced, like the frontend) whenever
     * the server reports a change to any of them. Emits [Registries.EMPTY] when no connection can be made.
     */
    @OptIn(FlowPreview::class)
    fun registries(): Flow<Registries> = channelFlow {
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (webSocket == null) {
            send(Registries.EMPTY)
            return@channelFlow
        }
        suspend fun fetch() {
            send(
                Registries(
                    entities = webSocket.result("config/entity_registry/list_for_display")
                        ?.let { (it as? JsonObject)?.let(::parseEntityRegistryDisplay) }.orEmpty(),
                    devices = (
                        webSocket.result(
                            "config/device_registry/list",
                        ) as? JsonArray
                        )?.let(::parseDeviceRegistry).orEmpty(),
                    areas = (
                        webSocket.result(
                            "config/area_registry/list",
                        ) as? JsonArray
                        )?.let(::parseAreaRegistry).orEmpty(),
                    floors = (
                        webSocket.result(
                            "config/floor_registry/list",
                        ) as? JsonArray
                        )?.let(::parseFloorRegistry).orEmpty(),
                ),
            )
        }
        launch {
            REGISTRY_EVENTS.map { event ->
                webSocket.subscribeRaw(SUBSCRIBE_EVENTS, mapOf("event_type" to event)) ?: emptyFlow()
            }.merge()
                .debounce(REGISTRY_REFETCH_DEBOUNCE)
                .collect { fetch() }
        }
        fetch()
        awaitClose()
    }

    /** User, server config and panels, or `null` when no connection can be made. */
    suspend fun serverInfo(): ServerInfo? {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return null
        val user = webSocket.result("auth/current_user") as? JsonObject
        val config = webSocket.result("get_config") as? JsonObject
        val panels = webSocket.result("get_panels") as? JsonObject
        return ServerInfo(
            user = user?.let {
                HassUser(
                    id = it.string("id").orEmpty(),
                    name = it.string("name"),
                    isAdmin = it.boolean("is_admin") == true,
                    isOwner = it.boolean("is_owner") == true,
                )
            },
            config = config?.let {
                HassConfig(
                    state = it.string("state"),
                    recoveryMode = it.boolean("recovery_mode") == true,
                    version = it.string("version"),
                    components = it.array("components")?.mapNotNull { component ->
                        component.stringOrNull
                    }?.toSet().orEmpty(),
                )
            } ?: HassConfig.UNKNOWN,
            panels = panels?.keys.orEmpty(),
        )
    }

    /** The home dashboard settings (`frontend/get_system_data {key: "home"}`), or `null` when unset. */
    suspend fun homeSystemData(): JsonObject? = (
        serverManager.webSocketRepositoryOrNull()
            ?.result("frontend/get_system_data", mapOf("key" to "home")) as? JsonObject
        )?.obj("value")

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

/** Server details that rarely change during a session. */
data class ServerInfo(val user: HassUser?, val config: HassConfig, val panels: Set<String>)

private suspend fun WebSocketRepository.result(type: String, data: Map<String, Any?> = emptyMap()): JsonElement? =
    sendRawMessage(mapOf("type" to type) + data)?.takeIf { it.success }?.result

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

private val REGISTRY_EVENTS = listOf(
    "entity_registry_updated",
    "device_registry_updated",
    "area_registry_updated",
    "floor_registry_updated",
)
private val REGISTRY_REFETCH_DEBOUNCE = 500.milliseconds
private const val SUBSCRIBE_ENTITIES = "subscribe_entities"
private const val SUBSCRIBE_EVENTS = "subscribe_events"
private const val EVENT_LOVELACE_UPDATED = "lovelace_updated"
