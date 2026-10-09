package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.entity.IconResources
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.model.DashboardConfig
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// The server responses the dashboards' data is derived from, as they are kept (and cached) together: one JSON
// object of the raw results by part, parsed the same way whether just loaded or read back from the cache.

/** Server resources used to display entities: icon translations and flat translation strings. */
data class EntityResources(val icons: IconResources, val translations: Map<String, String>)

/** What the navigation sidebar is computed from besides the panels: the user's and the system's settings. */
data class SidebarData(val userCore: JsonObject?, val systemCore: JsonObject?, val sidebar: JsonObject?)

/** The results of several requests as one object by part, or the first failure. */
fun bundle(vararg parts: Pair<String, Fetched<JsonElement?>>): Fetched<JsonObject> = bundle(parts.asList())

/** The results of several requests as one object by part, or the first failure. */
fun bundle(parts: List<Pair<String, Fetched<JsonElement?>>>): Fetched<JsonObject> {
    parts.firstNotNullOfOrNull { (_, part) -> part as? Fetched.Failure }?.let { return it }
    return Fetched.Success(
        JsonObject(parts.associate { (name, part) -> name to ((part as Fetched.Success).value ?: JsonNull) }),
    )
}

internal const val ENTITIES = "entities"
internal const val DEVICES = "devices"
internal const val AREAS = "areas"
internal const val FLOORS = "floors"
internal const val USER = "user"
internal const val CONFIG = "config"
internal const val PANELS = "panels"
internal const val ENERGY_PREFS = "energy_prefs"
internal const val ENERGY_SETTINGS = "energy_settings"
internal const val COMMON_CONTROLS = "common_controls"
internal const val COMPONENT_ICONS = "component_icons"
internal const val ENTITY_ICONS = "entity_icons"
internal const val COMPONENT_TRANSLATIONS = "component_translations"
internal const val ENTITY_TRANSLATIONS = "entity_translations"
internal const val USER_CORE = "user_core"
internal const val SYSTEM_CORE = "system_core"
internal const val SIDEBAR = "sidebar"
internal const val HOME = "home"
internal const val DASHBOARD_CONFIG = "dashboard_config"

internal fun parseRegistries(bundle: JsonObject): Fetched<Registries> =
    bundle.part<JsonObject>(ENTITIES).flatMap { entities ->
        bundle.part<JsonArray>(DEVICES).flatMap { devices ->
            bundle.part<JsonArray>(AREAS).flatMap { areas ->
                bundle.part<JsonArray>(FLOORS).map { floors ->
                    Registries(
                        entities = parseEntityRegistryDisplay(entities),
                        devices = parseDeviceRegistry(devices),
                        areas = parseAreaRegistry(areas),
                        floors = parseFloorRegistry(floors),
                    )
                }
            }
        }
    }

/**
 * Energy preferences and settings and common controls are absent (`null`) when not loaded or refused, as upstream
 * treats them.
 */
internal fun parseStrategyData(bundle: JsonObject): Fetched<StrategyData> = Fetched.Success(
    StrategyData(
        energyPrefs = bundle[ENERGY_PREFS] as? JsonObject,
        commonControls = (bundle[COMMON_CONTROLS] as? JsonObject)?.array("entities")?.mapNotNull { it.stringOrNull },
        energyHiddenCards = (bundle[ENERGY_SETTINGS] as? JsonObject)?.obj("value")?.array("hidden_cards")
            ?.mapNotNull { it.stringOrNull },
    ),
)

internal fun parseEntityResources(bundle: JsonObject): Fetched<EntityResources> =
    bundle.part<JsonObject>(COMPONENT_ICONS).flatMap { componentIcons ->
        bundle.part<JsonObject>(ENTITY_ICONS).flatMap { entityIcons ->
            bundle.translations(COMPONENT_TRANSLATIONS).flatMap { componentTranslations ->
                bundle.translations(ENTITY_TRANSLATIONS).map { entityTranslations ->
                    EntityResources(
                        icons = IconResources.fromResults(entityComponent = componentIcons, entity = entityIcons),
                        translations = componentTranslations + entityTranslations,
                    )
                }
            }
        }
    }

/** The flat strings of the `frontend/get_translations` result [name]. */
private fun JsonObject.translations(name: String): Fetched<Map<String, String>> =
    part<JsonObject>(name).flatMap { result ->
        result.obj("resources")?.mapNotNull { (key, value) -> value.stringOrNull?.let { key to it } }?.toMap()
            ?.let { Fetched.Success(it) } ?: unexpected(name)
    }

/** The part [name] of a bundle, as a [J]. */
private inline fun <reified J : JsonElement> JsonObject.part(name: String): Fetched<J> =
    (this[name] as? J)?.let { Fetched.Success(it) } ?: unexpected(name)

internal fun parseSidebarData(bundle: JsonObject): Fetched<SidebarData> =
    storedValue(bundle[USER_CORE]).flatMap { userCore ->
        storedValue(bundle[SYSTEM_CORE]).flatMap { systemCore ->
            storedValue(bundle[SIDEBAR]).map { SidebarData(userCore, systemCore, it) }
        }
    }

/** The `value` of a `frontend/get_*_data` result, `null` when nothing is stored. */
internal fun storedValue(result: JsonElement?): Fetched<JsonObject?> =
    (result as? JsonObject)?.let { Fetched.Success(it.obj("value")) } ?: unexpected("stored data")

internal fun parseStoredConfig(bundle: JsonObject): Fetched<StoredDashboardConfig> =
    when (val config = bundle[DASHBOARD_CONFIG]) {
        JsonNull -> Fetched.Success(StoredDashboardConfig.NotStored)
        is JsonObject -> Fetched.Success(StoredDashboardConfig.Stored(DashboardConfig(config)))
        else -> unexpected("dashboard config")
    }

internal fun unexpected(what: String) = Fetched.Failure(LoadError.UnexpectedResponse(what))
