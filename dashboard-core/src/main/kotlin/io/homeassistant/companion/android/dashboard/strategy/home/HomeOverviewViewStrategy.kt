package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
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
    if (floorsSections.isEmpty()) return welcomeEmptyState(homePanel)

    val favoriteEntities = config.array("favorite_entities")?.mapNotNull { it.stringOrNull }.orEmpty()
        .filter { it in states }
    val summaryCards = summaryCards(config, allEntities, data, homePanel)
    return buildJsonObject {
        put("type", "sections")
        put("max_columns", MAX_COLUMNS)
        put(
            "sections",
            JsonArray(
                listOfNotNull(favoritesSection(config, favoriteEntities), mobileSummarySection(summaryCards)) +
                    floorsSections,
            ),
        )
        if (config.boolean("hide_welcome_message") != true) put("header", welcomeHeader())
        putJsonObject("sidebar") {
            putJsonArray("sections") { add(sidebarSection(summaryCards)) }
            put("content_label", localize("ui.panel.lovelace.strategy.home.home"))
            put("sidebar_label", localize("ui.panel.lovelace.strategy.home.summaries"))
            putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
        }
    }
}

private fun HassSnapshot.summariesHeading() = buildJsonObject {
    put("type", "heading")
    put("heading", localize("ui.panel.lovelace.strategy.home.summaries"))
    put("heading_style", "title")
}

/**
 * The summaries on small screens, in the content. Summary cards are always present (repairs, updates, discovered
 * devices), so it always exists, as does the sidebar's.
 */
private fun HassSnapshot.mobileSummarySection(summaryCards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    put("column_span", MAX_COLUMNS)
    putJsonArray("visibility") { add(SMALL_SCREEN_CONDITION) }
    put(
        "cards",
        JsonArray(listOf(summariesHeading()) + summaryCards.map { it.withGridOptions(columns = MOBILE_COLUMNS) }),
    )
}

/** The summaries on large screens, in the sidebar. */
private fun HassSnapshot.sidebarSection(summaryCards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    val compactHeading =
        JsonObject(summariesHeading() + ("grid_options" to buildJsonObject { put("rows", "auto") }))
    put(
        "cards",
        JsonArray(listOf(compactHeading) + summaryCards.map { it.withGridOptions(columns = SIDEBAR_COLUMNS) }),
    )
}

private fun HassSnapshot.welcomeHeader() = buildJsonObject {
    put("layout", "responsive")
    putJsonObject("card") {
        put("type", "markdown")
        put("text_only", true)
        // The {{ user }} template is rendered by the markdown card
        put("content", "## " + localize("ui.panel.lovelace.strategy.home.welcome_user", mapOf("user" to "{{ user }}")))
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

internal const val MAX_OVERVIEW_COLUMNS = 3
private const val MAX_COLUMNS = MAX_OVERVIEW_COLUMNS
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
