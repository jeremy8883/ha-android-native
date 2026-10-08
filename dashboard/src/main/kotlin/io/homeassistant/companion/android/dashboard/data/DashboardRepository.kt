package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.RawWebSocketResponse
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.dashboard.derive.TemplateRequest
import io.homeassistant.companion.android.dashboard.derive.TemplateResult
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.IconResources
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.activeRepairsIssues
import io.homeassistant.companion.android.dashboard.entity.applyConfigFlowMessages
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
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
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

    /**
     * The active, non-ignored repair issues, fetched again (debounced, like the frontend) when the issue
     * registry changes. Empty when they cannot be read, for example for non-admin users.
     */
    @OptIn(FlowPreview::class)
    fun repairsIssues(): Flow<List<JsonObject>> = channelFlow {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return@channelFlow
        suspend fun fetch() {
            send((webSocket.result("repairs/list_issues") as? JsonObject)?.let(::activeRepairsIssues).orEmpty())
        }
        launch {
            (webSocket.subscribeRaw(SUBSCRIBE_EVENTS, mapOf("event_type" to REPAIRS_UPDATED_EVENT)) ?: emptyFlow())
                .debounce(REGISTRY_REFETCH_DEBOUNCE)
                .collect { fetch() }
        }
        fetch()
        awaitClose()
    }

    /** Config flows started by discovery, kept up to date through `config_entries/flow/subscribe`. */
    fun discoveredFlows(): Flow<List<JsonObject>> = flow {
        val events = serverManager.webSocketRepositoryOrNull()?.subscribeRaw(SUBSCRIBE_CONFIG_FLOWS) ?: return@flow
        emitAll(
            events.runningFold<JsonElement, List<JsonObject>?>(null) { flows, event ->
                applyConfigFlowMessages(flows, event)
            }.drop(1).filterNotNull(),
        )
    }

    /**
     * Call `[domain].[service]` (`call_service`), as the frontend's `callService` does.
     *
     * @return `null` when it succeeded, otherwise the server's error message (empty when there is none)
     */
    suspend fun callService(domain: String, service: String, data: JsonObject?, target: JsonObject?): String? {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return ""
        val message = buildMap<String, Any?> {
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            data?.let { put("service_data", it) }
            target?.let { put("target", it) }
        }
        val response = webSocket.sendRawMessage(message) ?: return ""
        return if (response.success) null else (response.error as? JsonObject)?.string("message").orEmpty()
    }

    /**
     * The default code stored for [entityId] (`options.[optionsDomain].default_code` of its registry entry), as
     * the frontend reads it before asking for a lock or alarm code. `null` when there is none or it can't be read.
     */
    suspend fun defaultCode(entityId: String, optionsDomain: String): String? = (
        serverManager.webSocketRepositoryOrNull()?.result(
            "config/entity_registry/get",
            mapOf("entity_id" to entityId),
        ) as? JsonObject
        )
        ?.obj("options")?.obj(optionsDomain)?.string("default_code")?.ifEmpty { null }

    /**
     * The renderings of [request], kept up to date by the server (`render_template`, strict like the markdown
     * card). When the subscription can't be made, the raw template is shown, as upstream falls back to.
     */
    fun renderTemplate(request: TemplateRequest): Flow<TemplateResult> = flow {
        val params = buildMap<String, Any?> {
            put("template", request.template)
            request.entityIds?.let { put("entity_ids", it) }
            put("variables", request.variables)
            put("strict", true)
        }
        val events = serverManager.webSocketRepositoryOrNull()?.subscribeRaw(RENDER_TEMPLATE, params)
        if (events == null) {
            emit(TemplateResult.Rendered(request.template))
            return@flow
        }
        emitAll(
            events.mapNotNull { event ->
                val result = event as? JsonObject ?: return@mapNotNull null
                result.string("result")?.let { TemplateResult.Rendered(it) }
                    ?: result.string("error")?.let { TemplateResult.Failed(it, result.string("level")) }
            },
        )
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
                    unitSystem = it.obj("unit_system")?.mapValues { unit ->
                        unit.value.stringOrNull.orEmpty()
                    }.orEmpty(),
                )
            } ?: HassConfig.UNKNOWN,
            panels = panels?.keys.orEmpty(),
        )
    }

    /**
     * Data that upstream strategies fetch while generating, fetched only for loaded integrations as upstream does.
     */
    suspend fun strategyData(components: Set<String>): StrategyData {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return StrategyData.NONE
        return StrategyData(
            // Errors (prefs not configured) mean no energy data, as upstream swallows them
            energyPrefs = if ("energy" in components) webSocket.result("energy/get_prefs") as? JsonObject else null,
            commonControls = if ("usage_prediction" in components) {
                (webSocket.result("usage_prediction/common_control") as? JsonObject)
                    ?.array("entities")?.mapNotNull { it.stringOrNull }
            } else {
                null
            },
        )
    }

    /**
     * The server's entity icon and state translations for [language], as the frontend loads them on connect
     * (`frontend/get_icons` and `frontend/get_translations` for the `entity_component` and `entity` categories).
     */
    suspend fun entityResources(language: String): EntityResources {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return EntityResources.NONE
        fun translations(category: String): suspend () -> Map<String, String> = {
            (
                webSocket.result(
                    "frontend/get_translations",
                    mapOf("language" to language, "category" to category),
                ) as? JsonObject
                )?.obj("resources")?.mapValues { it.value.stringOrNull.orEmpty() }.orEmpty()
        }
        return EntityResources(
            icons = IconResources.fromResults(
                entityComponent = webSocket.result(
                    "frontend/get_icons",
                    mapOf("category" to "entity_component"),
                ) as? JsonObject,
                entity = webSocket.result("frontend/get_icons", mapOf("category" to "entity")) as? JsonObject,
            ),
            translations = translations("entity_component")() + translations("entity")(),
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
/** Server resources used to display entities: icon translations and flat translation strings. */
data class EntityResources(val icons: IconResources, val translations: Map<String, String>) {
    companion object {
        val NONE = EntityResources(IconResources.EMPTY, emptyMap())
    }
}

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
private const val SUBSCRIBE_CONFIG_FLOWS = "config_entries/flow/subscribe"
private const val REPAIRS_UPDATED_EVENT = "repairs_issue_registry_updated"
private const val RENDER_TEMPLATE = "render_template"
