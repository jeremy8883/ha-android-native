package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.strategy.areaTileCard
import io.homeassistant.companion.android.dashboard.strategy.home.LARGE_SCREEN_CONDITION
import io.homeassistant.companion.android.dashboard.strategy.home.LIGHT_FILTERS
import io.homeassistant.companion.android.dashboard.strategy.home.SMALL_SCREEN_CONDITION
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The view of the lights panel: the lights of each area by floor, with buttons (small screens) or a toggle
 * group (large screens) to switch an area's lights, then the lights without an area.
 *
 * Port of `LightViewStrategy.generate` (frontend@20260624.6 src/panels/light/strategies/light-view-strategy.ts).
 * The strategy config is `{type: "light"}`.
 */
fun HassSnapshot.lightView(): JsonObject = summaryView(summarySections(LIGHT_VIEW).sections)

private val LIGHT_VIEW = SummaryViewSpec(
    filters = LIGHT_FILTERS,
    areaCards = { area, lights -> lightAreaCards(area, lights) },
    unassignedCard = { areaTileCard(it, prefix = "", includeFeature = false) },
    otherAreasKey = OTHER_AREAS_KEY,
    devicesKey = "ui.panel.lovelace.strategy.light.lights",
    otherDevicesKey = "ui.panel.lovelace.strategy.light.other_lights",
)

/** Port of `processAreasForLight` for one area. */
private fun HassSnapshot.lightAreaCards(area: AreaEntry, lights: List<String>): List<JsonObject> {
    if (lights.isEmpty()) return emptyList()
    val toggleGroup = buildJsonObject {
        put("type", "toggle-group")
        put("color", "amber")
        put("entities", JsonArray(lights.map(::JsonPrimitive)))
        putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
        putHalfRow()
    }
    val tiles = lights.flatMapIndexed { index, entityId ->
        // A blank card before every 3rd tile aligns the tiles with the toggle group on large screens
        val spacer = if (index % TILES_PER_ROW == 0 && index != 0) listOf(SPACER) else emptyList()
        spacer + areaTileCard(entityId, prefix = "", includeFeature = false)
    }
    return listOf(areaHeading(area, powerBadges(area.areaId, lights)), toggleGroup) + tiles
}

/** Buttons for small screens, turning the area's lights on when all are off and off when any is on. */
private fun HassSnapshot.powerBadges(areaId: String, lights: List<String>): JsonArray {
    val anyOn = buildJsonObject {
        put("condition", "or")
        putJsonArray("conditions") {
            lights.forEach { entityId ->
                addJsonObject {
                    put("condition", "state")
                    put("entity", entityId)
                    put("state", "on")
                }
            }
        }
    }
    val noneOn = buildJsonObject {
        put("condition", "not")
        putJsonArray("conditions") { add(anyOn) }
    }
    fun powerButton(textKey: String, action: String, visibility: JsonObject, color: String?) = buildJsonObject {
        put("type", "button")
        put("icon", "mdi:power")
        color?.let { put("color", it) }
        put("text", localize(textKey))
        putJsonObject("tap_action") {
            put("action", "perform-action")
            put("perform_action", action)
            putJsonObject("target") { put("area_id", areaId) }
        }
        putJsonArray("visibility") {
            add(SMALL_SCREEN_CONDITION)
            add(visibility)
        }
    }
    return buildJsonArray {
        add(powerButton("ui.panel.lovelace.strategy.light.off", "light.turn_on", noneOn, color = null))
        add(powerButton("ui.panel.lovelace.strategy.light.on", "light.turn_off", anyOn, color = "orange"))
    }
}

private fun JsonObjectBuilder.putHalfRow() {
    putJsonObject("grid_options") {
        put("columns", HALF_ROW_COLUMNS)
        put("rows", 1)
    }
}

private val SPACER = buildJsonObject {
    put("type", "vertical-stack")
    putJsonArray("cards") {}
    putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
    putHalfRow()
}

/** The toggle group and blank cards take half a section's width, as three tiles do. */
private const val HALF_ROW_COLUMNS = 6
private const val TILES_PER_ROW = 3
