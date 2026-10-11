package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.FloorEntry
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.home.defaultIcon

// The areas and floors a target picker offers. Port of `getAreasAndFloors` (frontend@20260624.6
// src/data/area_floor_picker.ts) as `ha-target-picker` calls it with an entity filter.

/**
 * Port of `getAreasAndFloors`: each floor (in registry order) followed by its areas, then the areas without a
 * floor; with a filter, only areas with a device or an entity (by its own area) that passes it, and the floors of
 * those. [excludedAreas] and [excludedFloors] are left out.
 */
fun TargetPickerSource.areaFloorItems(
    excludedAreas: List<String>,
    excludedFloors: List<String>,
): List<TargetPickerItem> {
    val registries = hass.registries
    val filteredEntities = filteredEntities(registries.entities.values.filter { it.areaId != null })
    val areaIds = if (entityFilter == null) {
        null
    } else {
        (filteredDevices().mapNotNull { it.areaId } + filteredEntities.orEmpty().mapNotNull { it.areaId }).toSet()
    }
    val areas = registries.areas.values
        .filter { areaIds == null || it.areaId in areaIds }
        .filter { it.areaId !in excludedAreas && (it.floorId == null || it.floorId !in excludedFloors) }
    val floorsWithAreas = areas.mapNotNull { it.floorId }.toSet()
    val floors = registries.floors.values
        .filter { it.floorId !in excludedFloors && (entityFilter == null || it.floorId in floorsWithAreas) }
    return floors.flatMap { floor ->
        val floorAreas = areas.filter { it.floorId == floor.floorId }
        listOf(floorItem(floor, floorAreas)) + floorAreas.map { areaItem(it, floor) }
    } + areas.filter { it.floorId == null }.map { areaItem(it, null) }
}

private fun floorItem(floor: FloorEntry, areas: List<AreaEntry>): TargetPickerItem {
    val name = floor.name.trim()
    val related = areas.flatMap { area ->
        listOfNotNull(area.areaId, area.name.trim().ifEmpty { null }) + area.aliases()
    }
    return TargetPickerItem(
        ref = TargetRef(TargetType.FLOOR, floor.floorId),
        primary = name,
        icon = TargetIcon.Named(floor.icon ?: floor.defaultIcon()),
        searchLabels = mapOf(
            "id" to floor.floorId,
            "name" to name.ifEmpty { null },
            "aliases" to floor.aliases().joinToString(", ").ifEmpty { null },
            "relatedAreas" to related.joinToString(" ").ifEmpty { null },
        ),
    )
}

private fun areaItem(area: AreaEntry, floor: FloorEntry?): TargetPickerItem {
    val name = area.name.trim()
    return TargetPickerItem(
        ref = TargetRef(TargetType.AREA, area.areaId),
        primary = name.ifEmpty { area.areaId },
        icon = TargetIcon.Named(area.icon ?: AREA_ICON),
        underFloor = area.floorId != null,
        searchLabels = mapOf(
            "id" to area.areaId,
            "name" to name.ifEmpty { null },
            "aliases" to area.aliases().joinToString(", ").ifEmpty { null },
        ) + listOfNotNull(floor?.let { "floorName" to it.name.trim().ifEmpty { null } }),
    )
}

private fun AreaEntry.aliases(): List<String> = json.array("aliases")?.mapNotNull { it.stringOrNull }.orEmpty()

private fun FloorEntry.aliases(): List<String> = json.array("aliases")?.mapNotNull { it.stringOrNull }.orEmpty()
