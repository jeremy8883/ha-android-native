package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.WebSocketConnectionStatus
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
import io.homeassistant.companion.android.dashboard.model.ERROR_CONFIG_NOT_FOUND
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.parsePanels
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** A dashboard's stored config, as `lovelace/config` returns it. */
sealed interface StoredDashboardConfig {
    data class Stored(val config: DashboardConfig) : StoredDashboardConfig

    /** The dashboard exists but has no stored config, so the frontend would generate one. */
    data object NotStored : StoredDashboardConfig
}

/**
 * Dashboard data from the active server. The data Flows are cold: their WebSocket subscriptions live only while
 * collected (`:common` restores them after a reconnection). Each loads its data when collected, again whenever the
 * server reports a change to it and after each reconnection, and keeps the last value loaded when an attempt fails
 * (see [KeptData]).
 */
class DashboardRepository @Inject constructor(
    private val serverManager: ServerManager,
    private val loadedData: LoadedData,
) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Load again everything that failed or is collected, now rather than at the next retry. */
    fun retry() {
        retries.tryEmit(Unit)
    }

    /** The connection's status, or `null` when there is no server. */
    fun connectionStatus(): Flow<WebSocketConnectionStatus?> = flow {
        val webSocket = serverManager.webSocketRepositoryOrNull()
        if (webSocket == null) emit(null) else emitAll(webSocket.connectionStatus())
    }

    /** All entity states, kept up to date through `subscribe_entities`. */
    fun entityStates(): Flow<Loadable<EntityStates>> = withServer { session ->
        session.kept<EntityStates>("states").subscribed(
            subscribe = { session.subscribe(SUBSCRIBE_ENTITIES) },
            reduce = { states, event ->
                // A snapshot (no current states) starts from no entities
                (event as? JsonObject)?.let { applyEntityEvent(states.orEmpty(), it) }
                    ?: null.also { Timber.w("Ignoring unexpected $SUBSCRIBE_ENTITIES event") }
            },
        )
    }

    /** The entity, device, area and floor registries, loaded again (debounced, like the frontend) on changes. */
    @OptIn(FlowPreview::class)
    fun registries(): Flow<Loadable<Registries>> = withServer { session ->
        session.upToDate(
            name = "registries",
            refreshes = REGISTRY_EVENTS.map { session.events(it) }.merge().debounce(REGISTRY_REFETCH_DEBOUNCE),
        ) {
            val entities = session.request("config/entity_registry/list_for_display").expect<JsonObject>()
            val devices = session.request("config/device_registry/list").expect<JsonArray>()
            val areas = session.request("config/area_registry/list").expect<JsonArray>()
            val floors = session.request("config/floor_registry/list").expect<JsonArray>()
            entities.flatMap { e ->
                devices.flatMap { d ->
                    areas.flatMap { a ->
                        floors.map { f ->
                            Registries(
                                entities = parseEntityRegistryDisplay(e),
                                devices = parseDeviceRegistry(d),
                                areas = parseAreaRegistry(a),
                                floors = parseFloorRegistry(f),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The active, non-ignored repair issues, loaded again (debounced, like the frontend) when the issue registry
     * changes. Only admins may read them.
     */
    @OptIn(FlowPreview::class)
    fun repairsIssues(): Flow<Loadable<List<JsonObject>>> = withServer { session ->
        session.upToDate(
            name = "repairs",
            refreshes = session.events(REPAIRS_UPDATED_EVENT).debounce(REGISTRY_REFETCH_DEBOUNCE),
        ) {
            session.request("repairs/list_issues").expect<JsonObject>().map(::activeRepairsIssues)
        }
    }

    /**
     * Config flows started by discovery, kept up to date through `config_entries/flow/subscribe`. Only admins may
     * subscribe.
     */
    fun discoveredFlows(): Flow<Loadable<List<JsonObject>>> = withServer { session ->
        session.kept<List<JsonObject>>("discovered-flows").subscribed(
            subscribe = { session.subscribe(SUBSCRIBE_CONFIG_FLOWS) },
            reduce = { flows, event -> applyConfigFlowMessages(flows, event) },
        )
    }

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
     * The default code stored for [entityId] (`options.[optionsDomain].default_code` of its registry entry), as
     * the frontend reads it before asking for a lock or alarm code; `null` when there is none.
     */
    suspend fun defaultCode(entityId: String, optionsDomain: String): Fetched<String?> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("config/entity_registry/get", mapOf("entity_id" to entityId)).expect<JsonObject>()
            .map { it.obj("options")?.obj(optionsDomain)?.string("default_code")?.ifEmpty { null } }
    }

    /**
     * The renderings of [request], kept up to date by the server (`render_template`, strict like the markdown
     * card). Until the subscription is made, the raw template is shown, as upstream falls back to; it is retried.
     */
    fun renderTemplate(request: TemplateRequest): Flow<TemplateResult> = flow {
        val params = buildMap<String, Any?> {
            put("template", request.template)
            request.entityIds?.let { put("entity_ids", it) }
            put("variables", request.variables)
            put("strict", true)
        }
        val webSocket = serverManager.webSocketRepositoryOrNull()
        var failures = 0
        var rendered = false
        while (webSocket != null) {
            val events = webSocket.subscribeRaw(RENDER_TEMPLATE, params)
            if (events != null) {
                failures = 0
                emitAll(
                    events.mapNotNull { event ->
                        val result = event as? JsonObject ?: return@mapNotNull null
                        result.string("result")?.let { TemplateResult.Rendered(it) }
                            ?: result.string("error")?.let { TemplateResult.Failed(it, result.string("level")) }
                    }.onEach { rendered = true },
                )
            }
            Timber.w("Failed to subscribe to $RENDER_TEMPLATE, or it ended; subscribing again")
            // Until it renders once, the raw template shows, as upstream falls back to; then the last rendering stays
            if (!rendered) emit(TemplateResult.Rendered(request.template))
            delay(RetryDelays.DEFAULT.after(failures++))
        }
        Timber.w("No server to render a template on")
        emit(TemplateResult.Rendered(request.template))
    }

    /**
     * The URL the active server is reached at right now (it changes between home and away networks), without a
     * trailing slash; `null` while there is no safe URL. Server paths such as entity pictures are resolved
     * against it, like the frontend's `hassUrl`.
     */
    fun serverUrl(): Flow<String?> = flow {
        emitAll(
            serverManager.connectionStateProvider().urlFlow().map { state ->
                (state as? UrlState.HasUrl)?.url?.toString()?.removeSuffix("/")
            },
        )
    }

    /**
     * A signed path to the latest snapshot of [cameraEntityId] (`auth/sign_path` for `/api/camera_proxy/...`),
     * as the frontend's `fetchThumbnailUrl` gets it.
     */
    suspend fun cameraSnapshotPath(cameraEntityId: String): Fetched<String> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("auth/sign_path", mapOf("path" to "/api/camera_proxy/$cameraEntityId"))
            .expect<JsonObject>()
            .flatMap { result ->
                result.string("path")?.let { Fetched.Success(it) }
                    ?: Fetched.Failure(LoadError.UnexpectedResponse("auth/sign_path"))
            }
    }

    /** The id of the active server, for links into the rest of the app. */
    suspend fun activeServerId(): Int? = serverManager.getServer()?.id

    /** The active server's id and how many servers there are, updated as servers change. */
    fun activeServer(): Flow<Pair<Int?, Int>> = serverManager.serversFlow.map { servers ->
        serverManager.getServer()?.id to servers.size
    }

    /** Make [serverId] the active server, which the native dashboards then show. */
    suspend fun activateServer(serverId: Int) = serverManager.activateServer(serverId)

    /** User, server config and panels. */
    fun serverInfo(): Flow<Loadable<ServerInfo>> = withServer { session ->
        session.upToDate(name = "server-info") { session.fetchServerInfo() }
    }

    /** The panels, loaded once, for telling which paths are dashboards. */
    suspend fun panels(): Fetched<Map<String, PanelInfo>> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("get_panels").expect<JsonObject>().map(::parsePanels)
    }

    /**
     * Data that upstream strategies fetch while generating, fetched only for loaded integrations as upstream does.
     */
    fun strategyData(components: Set<String>): Flow<Loadable<StrategyData>> = withServer { session ->
        session.upToDate(name = "strategy-data") {
            // As upstream (home-overview-view-strategy.ts), energy preferences the server refuses (not configured)
            // mean no energy data
            val energyPrefs = if ("energy" in components) {
                session.request("energy/get_prefs").absentWhenRefused().expectOrNull<JsonObject>()
            } else {
                Fetched.Success(null)
            }
            // Upstream fails the common controls section when the prediction is refused; it is left out instead
            val commonControls = if ("usage_prediction" in components) {
                session.request("usage_prediction/common_control").absentWhenRefused().expectOrNull<JsonObject>()
                    .map { result -> result?.array("entities")?.mapNotNull { it.stringOrNull } }
            } else {
                Fetched.Success(null)
            }
            energyPrefs.flatMap { energy -> commonControls.map { StrategyData(energy, it) } }
        }
    }

    /**
     * The server's entity icon and state translations for [language], as the frontend loads them on connect
     * (`frontend/get_icons` and `frontend/get_translations` for the `entity_component` and `entity` categories).
     */
    fun entityResources(language: String): Flow<Loadable<EntityResources>> = withServer { session ->
        session.upToDate(name = "entity-resources-$language") {
            suspend fun translations(category: String) = session.request(
                "frontend/get_translations",
                mapOf("language" to language, "category" to category),
            ).expect<JsonObject>().map { result ->
                result.obj("resources")?.mapNotNull { (key, value) -> value.stringOrNull?.let { key to it } }
                    .orEmpty().toMap()
            }
            val componentIcons = session.request("frontend/get_icons", mapOf("category" to "entity_component"))
                .expect<JsonObject>()
            val entityIcons = session.request("frontend/get_icons", mapOf("category" to "entity")).expect<JsonObject>()
            val componentTranslations = translations("entity_component")
            val entityTranslations = translations("entity")
            componentIcons.flatMap { component ->
                entityIcons.flatMap { entity ->
                    componentTranslations.flatMap { componentStrings ->
                        entityTranslations.map { entityStrings ->
                            EntityResources(
                                icons = IconResources.fromResults(entityComponent = component, entity = entity),
                                translations = componentStrings + entityStrings,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The settings the navigation sidebar depends on: the user's and the system's `core` data (default panel) and
     * the user's `sidebar` data (panel order, hidden panels).
     */
    fun sidebarData(): Flow<Loadable<SidebarData>> = withServer { session ->
        session.upToDate(name = "sidebar-data") {
            val userCore = session.storedValue("frontend/get_user_data", "core")
            val systemCore = session.storedValue("frontend/get_system_data", "core")
            val sidebar = session.storedValue("frontend/get_user_data", "sidebar")
            userCore.flatMap { user -> systemCore.flatMap { system -> sidebar.map { SidebarData(user, system, it) } } }
        }
    }

    /** The home dashboard settings (`frontend/get_system_data {key: "home"}`), `null` when unset. */
    fun homeSystemData(): Flow<Loadable<JsonObject?>> = withServer { session ->
        session.upToDate(name = "home-system-data") { session.storedValue("frontend/get_system_data", "home") }
    }

    /**
     * The stored config of the dashboard at [urlPath] (`null` for the default dashboard), loaded again whenever the
     * server reports a `lovelace_updated` event for it.
     */
    fun dashboardConfig(urlPath: String?): Flow<Loadable<StoredDashboardConfig>> = withServer { session ->
        session.upToDate(
            name = "dashboard-config/${urlPath.orEmpty()}",
            refreshes = session.events(EVENT_LOVELACE_UPDATED).filter { it.updatedUrlPath() == urlPath }
                .onEach { Timber.d("Dashboard config updated, refetching") },
        ) {
            when (val result = session.request("lovelace/config", mapOf("url_path" to urlPath, "force" to false))) {
                is Fetched.Failure -> if ((result.error as? LoadError.Server)?.code == ERROR_CONFIG_NOT_FOUND) {
                    Fetched.Success(StoredDashboardConfig.NotStored)
                } else {
                    result
                }
                is Fetched.Success -> Fetched.Success(result.value).expect<JsonObject>()
                    .map { StoredDashboardConfig.Stored(DashboardConfig(it)) }
            }
        }
    }

    /** [block]'s data for the active server, or failed when there is none. */
    private fun <T> withServer(block: (ServerSession) -> Flow<Loadable<T>>): Flow<Loadable<T>> = flow {
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
private class ServerSession(
    val serverId: Int,
    val webSocket: WebSocketRepository,
    private val loadedData: LoadedData,
    private val retries: Flow<Unit>,
) {
    val connection = webSocket.connectionStatus()

    /** [name] of this server, kept up to date from what was last loaded (see [KeptData]). */
    fun <T> kept(name: String): KeptData<T> = KeptData(name, loadedData.keeper(serverId, name), connection)

    /**
     * [fetch]'s value kept up to date as [name] (see [KeptData.fetched]), loaded again on each [refreshes] emission
     * and on the user's retries.
     */
    fun <T> upToDate(
        name: String,
        refreshes: Flow<Any?> = emptyFlow(),
        fetch: suspend () -> Fetched<T>,
    ): Flow<Loadable<T>> = kept<T>(name).fetched(merge(refreshes.map {}, retries), fetch)

    suspend fun request(type: String, data: Map<String, Any?> = emptyMap()): Fetched<JsonElement?> =
        webSocket.request(type, data)

    suspend fun subscribe(type: String): Fetched<Flow<JsonElement>> =
        webSocket.subscribeRaw(type)?.let { Fetched.Success(it) } ?: Fetched.Failure(LoadError.NoResponse)

    /** The `value` stored under [key] by a `frontend/get_*_data` command, `null` when nothing is stored. */
    suspend fun storedValue(type: String, key: String): Fetched<JsonObject?> =
        request(type, mapOf("key" to key)).expect<JsonObject>().map { it.obj("value") }

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

    suspend fun fetchServerInfo(): Fetched<ServerInfo> {
        val user = request("auth/current_user").expect<JsonObject>().flatMap(::parseUser)
        val config = request("get_config").expect<JsonObject>().flatMap(::parseConfig)
        val panels = request("get_panels").expect<JsonObject>()
        return user.flatMap { u ->
            config.flatMap { c ->
                panels.map { p -> ServerInfo(user = u, config = c, panels = p.keys, panelInfo = parsePanels(p)) }
            }
        }
    }
}

private fun parseUser(user: JsonObject): Fetched<HassUser> {
    val id = user.string("id") ?: return Fetched.Failure(LoadError.UnexpectedResponse("auth/current_user"))
    return Fetched.Success(
        HassUser(
            id = id,
            name = user.string("name"),
            isAdmin = user.boolean("is_admin") == true,
            isOwner = user.boolean("is_owner") == true,
        ),
    )
}

private fun parseConfig(config: JsonObject): Fetched<HassConfig> {
    val components = config.array("components") ?: return Fetched.Failure(LoadError.UnexpectedResponse("get_config"))
    return Fetched.Success(
        HassConfig(
            state = config.string("state"),
            recoveryMode = config.boolean("recovery_mode") == true,
            version = config.string("version"),
            components = components.mapNotNull { it.stringOrNull }.toSet(),
            // Measures without a unit are left out
            unitSystem = config.obj("unit_system")?.mapNotNull { (measure, unit) ->
                unit.stringOrNull?.let {
                    measure to
                        it
                }
            }
                .orEmpty().toMap(),
        ),
    )
}

/** Server resources used to display entities: icon translations and flat translation strings. */
data class EntityResources(val icons: IconResources, val translations: Map<String, String>)

/**
 * @property panels the url paths of the registered panels
 * @property panelInfo the panels with their titles, icons and visibility, for the navigation sidebar
 */
data class ServerInfo(
    val user: HassUser?,
    val config: HassConfig,
    val panels: Set<String>,
    val panelInfo: Map<String, PanelInfo>,
)

/** What the navigation sidebar is computed from besides the panels: the user's and the system's settings. */
data class SidebarData(val userCore: JsonObject?, val systemCore: JsonObject?, val sidebar: JsonObject?)

/** The outcome of the [type] command: its result (which may be `null`), or why there is none. */
private suspend fun WebSocketRepository.request(
    type: String,
    data: Map<String, Any?> = emptyMap(),
): Fetched<JsonElement?> {
    val response = sendRawMessage(mapOf("type" to type) + data)
    return when {
        response == null -> Fetched.Failure(LoadError.NoResponse)
        response.success -> Fetched.Success(response.result)
        else -> (response.error as? JsonObject).let { error ->
            Fetched.Failure(LoadError.Server(code = error?.string("code"), message = error?.string("message")))
        }
    }
}

/** The result as a [J], or failed when it is something else. */
private inline fun <reified J : JsonElement> Fetched<JsonElement?>.expect(): Fetched<J> = flatMap { result ->
    (result as? J)?.let { Fetched.Success(it) }
        ?: Fetched.Failure(LoadError.UnexpectedResponse(J::class.simpleName.orEmpty()))
}

/** The result as a [J], `null` when there is none, or failed when it is something else. */
private inline fun <reified J : JsonElement> Fetched<JsonElement?>.expectOrNull(): Fetched<J?> = flatMap { result ->
    when (result) {
        null, JsonNull -> Fetched.Success(null)
        is J -> Fetched.Success(result)
        else -> Fetched.Failure(LoadError.UnexpectedResponse(J::class.simpleName.orEmpty()))
    }
}

/** A refusal by the server as no result, for data upstream treats as absent when refused. */
private fun Fetched<JsonElement?>.absentWhenRefused(): Fetched<JsonElement?> =
    if ((this as? Fetched.Failure)?.error is LoadError.Server) Fetched.Success(null) else this

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
