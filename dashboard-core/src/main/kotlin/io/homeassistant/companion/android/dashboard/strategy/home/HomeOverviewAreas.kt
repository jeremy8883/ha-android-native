package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.FloorEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.strategy.AreasFloorHierarchy
import io.homeassistant.companion.android.dashboard.strategy.areasFloorHierarchy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// The areas part of the home overview (HomeOverviewViewStrategy.generate, frontend@20260624.6
// src/panels/lovelace/strategies/home/home-overview-view-strategy.ts)

/** One section per floor with its area cards, then a section for areas without a floor and other devices. */
internal fun HassSnapshot.floorsSections(allEntities: List<String>): List<JsonObject> {
    val hierarchy = areasFloorHierarchy()
    val floorCount = hierarchy.floors.size + if (hierarchy.unassignedAreas.isNotEmpty()) 1 else 0
    val floorSections = hierarchy.floors.filter { (_, areaIds) -> areaIds.isNotEmpty() }.map { (floorId, areaIds) ->
        val floor = registries.floors.getValue(floorId)
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", if (floorCount > 1) floor.name else localize("ui.panel.lovelace.strategy.home.areas"))
            put("heading_style", "title")
            put("icon", floor.icon?.ifEmpty { null } ?: floor.defaultIcon())
        }
        titledSection(listOf(heading) + areaIds.map(::areaCard))
    }
    return floorSections + listOfNotNull(otherAreasSection(hierarchy, allEntities))
}

/** The areas without a floor and a tile for the devices without an area, when there are any. */
private fun HassSnapshot.otherAreasSection(hierarchy: AreasFloorHierarchy, allEntities: List<String>): JsonObject? {
    val entitiesWithoutAreas = findEntities(allEntities, OTHER_DEVICES_FILTERS)
    if (hierarchy.unassignedAreas.isEmpty() && entitiesWithoutAreas.isEmpty()) return null
    val cards = hierarchy.unassignedAreas.map(::areaCard) +
        listOfNotNull(otherDevicesTile().takeIf { entitiesWithoutAreas.isNotEmpty() })
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
    return titledSection(listOfNotNull(headingCard) + cards)
}

private fun titledSection(cards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    put("column_span", MAX_OVERVIEW_COLUMNS)
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
        put("columns", AREA_CARD_COLUMNS)
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
        put("columns", AREA_CARD_COLUMNS)
    }
}

/** Port of `floorDefaultIcon` (src/components/ha-floor-icon.ts). */
internal fun FloorEntry.defaultIcon(): String = when (level) {
    -1 -> "mdi:home-floor-negative-1"
    in 0..HIGHEST_NUMBERED_FLOOR -> "mdi:home-floor-$level"
    else -> "mdi:home"
}

/** The highest floor level with its own icon. */
private const val HIGHEST_NUMBERED_FLOOR = 3

/** Area cards take a third of a section's width. */
private const val AREA_CARD_COLUMNS = 4
