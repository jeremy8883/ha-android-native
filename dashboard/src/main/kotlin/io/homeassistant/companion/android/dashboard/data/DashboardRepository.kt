package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.model.DashboardConfig
import io.homeassistant.companion.android.dashboard.model.ERROR_CONFIG_NOT_FOUND
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** A dashboard's stored config, as `lovelace/config` returns it. */
sealed interface StoredDashboardConfig {
    data class Stored(val config: DashboardConfig) : StoredDashboardConfig

    /** The dashboard exists but has no stored config, so the frontend would generate one. */
    data object NotStored : StoredDashboardConfig
}

/**
 * What the active server's dashboards are made of: their configs and the registries, server details and translations
 * they are derived from. Each Flow loads its data when collected, again whenever the server reports a change to it and
 * after each reconnection, and keeps the last value loaded when an attempt fails (see [KeptData]).
 */
class DashboardRepository @Inject constructor(private val sessions: ServerSessions) {
    /** Load again everything that failed or is collected, now rather than at the next retry. */
    fun retry() = sessions.retry()

    /** The entity, device, area and floor registries, loaded again (debounced, like the frontend) on changes. */
    @OptIn(FlowPreview::class)
    fun registries(): Flow<Loadable<Registries>> = sessions.withServer { session ->
        session.parsed(
            name = "registries",
            refreshes = REGISTRY_EVENTS.map { session.events(it) }.merge().debounce(REGISTRY_REFETCH_DEBOUNCE),
            parse = ::parseRegistries,
        ) {
            bundle(
                ENTITIES to session.request("config/entity_registry/list_for_display"),
                DEVICES to session.request("config/device_registry/list"),
                AREAS to session.request("config/area_registry/list"),
                FLOORS to session.request("config/floor_registry/list"),
            )
        }
    }

    /** User, server config and panels. */
    fun serverInfo(): Flow<Loadable<ServerInfo>> = sessions.withServer { session ->
        session.parsed(name = "server-info", parse = ::parseServerInfo) {
            bundle(
                USER to session.request("auth/current_user"),
                CONFIG to session.request("get_config"),
                PANELS to session.request("get_panels"),
            )
        }
    }

    /**
     * Data that upstream strategies fetch while generating, fetched only for loaded integrations as upstream does.
     */
    fun strategyData(components: Set<String>): Flow<Loadable<StrategyData>> = sessions.withServer { session ->
        session.parsed(name = "strategy-data", parse = ::parseStrategyData) {
            bundle(
                // As upstream (home-overview-view-strategy.ts), energy preferences the server refuses (not
                // configured) mean no energy data
                ENERGY_PREFS to if ("energy" in components) {
                    session.request("energy/get_prefs").absentWhenRefused()
                } else {
                    Fetched.Success(null)
                },
                // The energy panel reads its settings that way too (ha-panel-energy.ts _loadSystemData)
                ENERGY_SETTINGS to if ("energy" in components) {
                    session.request("frontend/get_system_data", mapOf("key" to "energy")).absentWhenRefused()
                } else {
                    Fetched.Success(null)
                },
                // Upstream fails the common controls section when the prediction is refused; it is left out instead
                COMMON_CONTROLS to if ("usage_prediction" in components) {
                    session.request("usage_prediction/common_control").absentWhenRefused()
                } else {
                    Fetched.Success(null)
                },
            )
        }
    }

    /**
     * The server's entity icon and state translations for [language], as the frontend loads them on connect
     * (`frontend/get_icons` and `frontend/get_translations` for the `entity_component` and `entity` categories).
     */
    fun entityResources(language: String): Flow<Loadable<EntityResources>> = sessions.withServer { session ->
        suspend fun translations(category: String) = session.request(
            "frontend/get_translations",
            mapOf("language" to language, "category" to category),
        )
        session.parsed(name = "entity-resources-$language", parse = ::parseEntityResources) {
            bundle(
                COMPONENT_ICONS to session.request("frontend/get_icons", mapOf("category" to "entity_component")),
                ENTITY_ICONS to session.request("frontend/get_icons", mapOf("category" to "entity")),
                COMPONENT_TRANSLATIONS to translations("entity_component"),
                ENTITY_TRANSLATIONS to translations("entity"),
            )
        }
    }

    /**
     * The settings the navigation sidebar depends on: the user's and the system's `core` data (default panel) and
     * the user's `sidebar` data (panel order, hidden panels).
     */
    fun sidebarData(): Flow<Loadable<SidebarData>> = sessions.withServer { session ->
        session.parsed(name = "sidebar-data", parse = ::parseSidebarData) {
            bundle(
                USER_CORE to session.request("frontend/get_user_data", mapOf("key" to "core")),
                SYSTEM_CORE to session.request("frontend/get_system_data", mapOf("key" to "core")),
                SIDEBAR to session.request("frontend/get_user_data", mapOf("key" to "sidebar")),
            )
        }
    }

    /** The home dashboard settings (`frontend/get_system_data {key: "home"}`), `null` when unset. */
    fun homeSystemData(): Flow<Loadable<JsonObject?>> = sessions.withServer { session ->
        session.parsed(name = "home-system-data", parse = { bundle -> storedValue(bundle[HOME]) }) {
            bundle(HOME to session.request("frontend/get_system_data", mapOf("key" to "home")))
        }
    }

    /**
     * The stored config of the dashboard at [urlPath] (`null` for the default dashboard), loaded again whenever the
     * server reports a `lovelace_updated` event for it.
     */
    fun dashboardConfig(urlPath: String?): Flow<Loadable<StoredDashboardConfig>> = sessions.withServer { session ->
        session.parsed(
            name = "dashboard-config/${urlPath.orEmpty()}",
            refreshes = session.events(EVENT_LOVELACE_UPDATED).filter { it.updatedUrlPath() == urlPath }
                .onEach { Timber.d("Dashboard config updated, refetching") },
            parse = ::parseStoredConfig,
        ) {
            val result = session.request("lovelace/config", mapOf("url_path" to urlPath, "force" to false))
            // Without a stored config the server refuses with `config_not_found`, kept as no config
            val notStored = ((result as? Fetched.Failure)?.error as? LoadError.Server)?.code == ERROR_CONFIG_NOT_FOUND
            bundle(DASHBOARD_CONFIG to if (notStored) Fetched.Success(null) else result)
        }
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
private const val EVENT_LOVELACE_UPDATED = "lovelace_updated"
