package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.FloorEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import io.homeassistant.companion.android.dashboard.strategy.areasFloorHierarchy
import io.homeassistant.companion.android.dashboard.strategy.commonControlTile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The home dashboard's overview: favourites (via the `common-controls` section strategy), summaries, and the
 * areas grouped by floor, with a welcome header and a summaries sidebar on large screens.
 *
 * Port of `HomeOverviewViewStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/home/home-overview-view-strategy.ts). [config] is the view's strategy config.
 */
fun HassSnapshot.homeOverviewView(config: JsonObject, data: StrategyData): JsonObject {
    val homePanel = config.boolean("home_panel") == true
    val allEntities = states.keys.toList()

    val floorsSections = floorsSections(allEntities)

    val favoriteEntities = config.array("favorite_entities")?.mapNotNull { it.stringOrNull }.orEmpty().filter {
        it in
            states
    }
    val favoritesSection = favoritesSection(config, favoriteEntities)

    val summaryCards = summaryCards(config, allEntities, data, homePanel)
    val summaryHeading = buildJsonObject {
        put("type", "heading")
        put("heading", localize("ui.panel.lovelace.strategy.home.summaries"))
        put("heading_style", "title")
    }
    // Summary cards are always present (repairs, updates, discovered devices), so both sections always exist
    val mobileSummarySection = buildJsonObject {
        put("type", "grid")
        put("column_span", MAX_COLUMNS)
        putJsonArray("visibility") { add(SMALL_SCREEN_CONDITION) }
        put(
            "cards",
            JsonArray(
                listOf(summaryHeading) + summaryCards.map { it.withGridOptions(columns = MOBILE_COLUMNS) },
            ),
        )
    }
    val sidebarSection = buildJsonObject {
        put("type", "grid")
        val compactHeading = JsonObject(summaryHeading + ("grid_options" to buildJsonObject { put("rows", "auto") }))
        put(
            "cards",
            JsonArray(
                listOf(compactHeading) + summaryCards.map { it.withGridOptions(columns = SIDEBAR_COLUMNS) },
            ),
        )
    }

    if (floorsSections.isEmpty()) return welcomeEmptyState(homePanel)

    return buildJsonObject {
        put("type", "sections")
        put("max_columns", MAX_COLUMNS)
        put("sections", JsonArray(listOfNotNull(favoritesSection, mobileSummarySection) + floorsSections))
        if (config.boolean("hide_welcome_message") != true) {
            putJsonObject("header") {
                put("layout", "responsive")
                putJsonObject("card") {
                    put("type", "markdown")
                    put("text_only", true)
                    // The {{ user }} template is rendered by the markdown card
                    put(
                        "content",
                        "## " + localize("ui.panel.lovelace.strategy.home.welcome_user", mapOf("user" to "{{ user }}")),
                    )
                }
            }
        }
        putJsonObject("sidebar") {
            putJsonArray("sections") { add(sidebarSection) }
            put("content_label", localize("ui.panel.lovelace.strategy.home.home"))
            put("sidebar_label", localize("ui.panel.lovelace.strategy.home.summaries"))
            putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
        }
    }
}

/** One section per floor with its area cards, then a section for areas without a floor and other devices. */
private fun HassSnapshot.floorsSections(allEntities: List<String>): List<JsonObject> {
    val hierarchy = areasFloorHierarchy()
    val floorCount = hierarchy.floors.size + if (hierarchy.unassignedAreas.isNotEmpty()) 1 else 0
    val sections = mutableListOf<JsonObject>()

    for ((floorId, areaIds) in hierarchy.floors) {
        if (areaIds.isEmpty()) continue
        val floor = registries.floors.getValue(floorId)
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", if (floorCount > 1) floor.name else localize("ui.panel.lovelace.strategy.home.areas"))
            put("heading_style", "title")
            put("icon", floor.icon?.ifEmpty { null } ?: floor.defaultIcon())
        }
        sections += titledSection(listOf(heading) + areaIds.map(::areaCard))
    }

    val entitiesWithoutAreas = findEntities(allEntities, OTHER_DEVICES_FILTERS)
    if (hierarchy.unassignedAreas.isNotEmpty() || entitiesWithoutAreas.isNotEmpty()) {
        val cards = hierarchy.unassignedAreas.map(::areaCard).toMutableList()
        if (entitiesWithoutAreas.isNotEmpty()) cards += otherDevicesTile()

        val noOtherAreas = hierarchy.unassignedAreas.isEmpty()
        val noFloor = hierarchy.floors.isEmpty()
        val heading = when {
            noFloor && noOtherAreas -> null
            noFloor -> localize("ui.panel.lovelace.strategy.home.areas")
            noOtherAreas -> localize("ui.panel.lovelace.strategy.home.devices")
            else -> localize("ui.panel.lovelace.strategy.home.other_areas")
        }
        val headingCard = heading?.let {
            buildJsonObject {
                put("type", "heading")
                put("heading", it)
                put("heading_style", "title")
            }
        }
        sections += titledSection(listOfNotNull(headingCard) + cards)
    }
    return sections
}

private fun titledSection(cards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    put("column_span", MAX_COLUMNS)
    put("cards", JsonArray(cards))
}

/** Port of `computeAreaCard`. */
private fun HassSnapshot.areaCard(areaId: String): JsonObject = buildJsonObject {
    put("type", "area")
    put("area", areaId)
    put("display_type", "compact")
    putJsonArray("sensor_classes") {
        if (registries.areas[areaId]?.temperatureEntityId != null) add("temperature")
    }
    putJsonObject("tap_action") {
        put("action", "navigate")
        put("navigation_path", "areas-$areaId")
    }
    put("vertical", true)
    putJsonObject("grid_options") {
        put("rows", 2)
        put("columns", 4)
    }
}

/** A tile on `zone.home`, which always exists, standing for devices without an area. */
private fun HassSnapshot.otherDevicesTile(): JsonObject = buildJsonObject {
    put("type", "tile")
    put("entity", "zone.home")
    put("vertical", true)
    put("name", localize("ui.panel.lovelace.strategy.home.devices"))
    put("icon", "mdi:devices")
    put("hide_state", true)
    putJsonObject("tap_action") {
        put("action", "navigate")
        put("navigation_path", "other-devices")
    }
    putJsonObject("grid_options") {
        put("rows", 2)
        put("columns", 4)
    }
}

private fun HassSnapshot.favoritesSection(config: JsonObject, favoriteEntities: List<String>): JsonObject? {
    val heading = buildJsonObject {
        put("type", "heading")
        put("heading", localize("ui.panel.lovelace.strategy.home.favorites"))
        put("heading_style", "title")
        putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
        putJsonObject("grid_options") { put("rows", "auto") }
    }
    return when {
        config.boolean("hide_suggested_entities") != true -> buildJsonObject {
            putJsonObject("strategy") {
                put("type", "common-controls")
                put("limit", maxOf(MIN_COMMON_CONTROLS, favoriteEntities.size))
                putJsonArray("include_entities") { favoriteEntities.forEach(::add) }
                put("hide_empty", true)
                put("heading", heading)
            }
            put("column_span", MAX_COLUMNS)
        }
        favoriteEntities.isNotEmpty() -> buildJsonObject {
            put("type", "grid")
            put("column_span", MAX_COLUMNS)
            put("cards", JsonArray(listOf(heading) + favoriteEntities.map(::commonControlTile)))
        }
        else -> null
    }
}

/** Repairs, updates and discovered devices, then the summaries and custom shortcuts in the user's order. */
private fun HassSnapshot.summaryCards(
    config: JsonObject,
    allEntities: List<String>,
    data: StrategyData,
    homePanel: Boolean,
): List<JsonObject> {
    val cards = mutableListOf(
        adminCard("repairs", "/config/repairs?historyBack=1"),
        adminCard("updates", "/config/updates?historyBack=1"),
        buildJsonObject {
            put("type", "discovered-devices")
            put("hide_empty", true)
        },
    )
    for (item in resolveShortcutItems(config["shortcuts"] as? JsonArray)) {
        if (item.string("type") == "summary") {
            if (item.boolean("hidden") == true) continue
            summaryCard(item.string("key"), allEntities, data, homePanel)?.let(cards::add)
        } else {
            cards += buildJsonObject {
                put("type", "shortcut")
                item.string("label")?.let { put("label", it) }
                item.string("icon")?.let { put("icon", it) }
                item.string("color")?.let { put("color", it) }
                putJsonObject("tap_action") {
                    put("action", "navigate")
                    item["path"]?.let { put("navigation_path", it) }
                }
            }
        }
    }
    return cards
}

private fun adminCard(type: String, path: String) = buildJsonObject {
    put("type", type)
    put("hide_empty", true)
    putJsonObject("tap_action") {
        put("action", "navigate")
        put("navigation_path", path)
    }
}

/** Port of the `summaryCardBuilders` entries: the card for a summary key, or `null` when it has nothing to show. */
private fun HassSnapshot.summaryCard(
    key: String?,
    allEntities: List<String>,
    data: StrategyData,
    homePanel: Boolean,
): JsonObject? {
    fun has(summary: HomeSummary) = findEntities(allEntities, summary.filters).isNotEmpty()
    fun summary(name: String, path: String) = buildJsonObject {
        put("type", "home-summary")
        put("summary", name)
        putJsonObject("tap_action") {
            put("action", "navigate")
            put("navigation_path", path)
        }
    }
    val backToHome = if (homePanel) "&backPath=/home" else ""
    return when (key) {
        "light" -> summary("light", "/light?historyBack=1").takeIf { "light" in panels && has(HomeSummary.LIGHT) }
        "climate" -> summary("climate", "/climate?historyBack=1").takeIf {
            "climate" in panels &&
                has(HomeSummary.CLIMATE)
        }
        "security" -> summary("security", "/security?historyBack=1").takeIf {
            "security" in panels &&
                has(HomeSummary.SECURITY)
        }
        "media_players" -> summary("media_players", "media-players").takeIf { has(HomeSummary.MEDIA_PLAYERS) }
        "maintenance" -> summary("maintenance", "/maintenance?historyBack=1$backToHome")
            .takeIf { "maintenance" in panels && has(HomeSummary.MAINTENANCE) }
        "weather" -> weatherEntity()?.let { entityId ->
            buildJsonObject {
                put("type", "tile")
                put("entity", entityId)
                put("name", localize("ui.panel.lovelace.strategy.home.summary_list.weather"))
                putJsonArray("state_content") {
                    add("temperature")
                    add("state")
                }
            }
        }
        "energy" -> summary("energy", "/energy?historyBack=1$backToHome").takeIf {
            "energy" in panels &&
                hasGridEnergy(data)
        }
        else -> null
    }
}

private fun HassSnapshot.weatherEntity(): String? = filterEntities(
    states.keys.toList(),
    EntityFilter(domains = setOf("weather"), entityCategories = setOf(ENTITY_CATEGORY_NONE)),
)
    .minOrNull()

private fun HassSnapshot.hasGridEnergy(data: StrategyData): Boolean {
    // Upstream only asks for the prefs when the energy integration is loaded
    if ("energy" !in config.components) return false
    return data.energyPrefs?.objects("energy_sources")
        ?.any { it.string("type") == "grid" && !it.string("stat_energy_from").isNullOrEmpty() } == true
}

private fun HassSnapshot.welcomeEmptyState(homePanel: Boolean): JsonObject = buildJsonObject {
    put("type", "panel")
    putJsonArray("cards") {
        addJsonObject {
            put("type", "empty-state")
            put("icon", "mdi:home-assistant")
            put("icon_color", "primary")
            put("content_only", true)
            put("title", localize("ui.panel.lovelace.strategy.home.welcome_title"))
            put("content", localize("ui.panel.lovelace.strategy.home.welcome_content"))
            if (homePanel && user?.isAdmin == true) {
                putJsonArray("buttons") {
                    addJsonObject {
                        put("icon", "mdi:plus")
                        put("text", localize("ui.panel.lovelace.strategy.home.welcome_add_device"))
                        put("appearance", "filled")
                        put("variant", "brand")
                        putJsonObject("tap_action") {
                            put("action", "fire-dom-event")
                            putJsonObject("home_panel") { put("type", "add_integration") }
                        }
                    }
                    addJsonObject {
                        put("icon", "mdi:home-edit")
                        put("text", localize("ui.panel.lovelace.strategy.home.welcome_edit_areas"))
                        put("appearance", "plain")
                        put("variant", "brand")
                        putJsonObject("tap_action") {
                            put("action", "navigate")
                            put("navigation_path", "/config/areas/dashboard")
                        }
                    }
                }
            }
        }
    }
}

private fun JsonObject.withGridOptions(columns: Int): JsonObject =
    JsonObject(this + ("grid_options" to buildJsonObject { put("columns", columns) }))

/** Port of `floorDefaultIcon` (src/components/ha-floor-icon.ts). */
private fun FloorEntry.defaultIcon(): String = when (level) {
    0 -> "mdi:home-floor-0"
    1 -> "mdi:home-floor-1"
    2 -> "mdi:home-floor-2"
    3 -> "mdi:home-floor-3"
    -1 -> "mdi:home-floor-negative-1"
    else -> "mdi:home"
}

private const val MAX_COLUMNS = 3
private const val MOBILE_COLUMNS = 6
private const val SIDEBAR_COLUMNS = 12
private const val MIN_COMMON_CONTROLS = 8

/** Port of `LARGE_SCREEN_CONDITION` (src/panels/lovelace/strategies/helpers/view-columns-conditions.ts). */
val LARGE_SCREEN_CONDITION = buildJsonObject {
    put("condition", "view_columns")
    put("min", 2)
}

/** Port of `SMALL_SCREEN_CONDITION`. */
val SMALL_SCREEN_CONDITION = buildJsonObject {
    put("condition", "view_columns")
    put("max", 1)
}

/** Port of `OTHER_DEVICES_FILTERS` (src/panels/lovelace/strategies/home/helpers/other-devices-filters.ts). */
val OTHER_DEVICES_FILTERS = listOf(
    EntityFilter(
        areas = setOf(null),
        hiddenPlatforms = setOf("automation", "script", "hassio", "backup", "mobile_app", "zone", "person"),
        hiddenDomains = setOf(
            "ai_task", "automation", "configurator", "device_tracker", "event", "geo_location", "notify",
            "persistent_notification", "script", "sun", "tag", "todo", "zone",
            // ASSIST_ENTITIES (src/common/const.ts)
            "assist_satellite", "conversation", "stt", "tts",
        ),
    ),
)
