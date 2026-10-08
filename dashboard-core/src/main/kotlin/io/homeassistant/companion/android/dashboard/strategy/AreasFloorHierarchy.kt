package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot

/** Areas grouped by floor (in floor registry order), plus areas without a floor. */
data class AreasFloorHierarchy(val floors: List<Pair<String, List<String>>>, val unassignedAreas: List<String>)

/** Port of `getAreasFloorHierarchy` (frontend@20260624.6 src/common/areas/areas-floor-hierarchy.ts). */
fun HassSnapshot.areasFloorHierarchy(): AreasFloorHierarchy {
    val (withFloor, withoutFloor) = registries.areas.values.partition { it.floorId != null }
    val byFloor = withFloor.groupBy({ it.floorId }, { it.areaId })
    return AreasFloorHierarchy(
        floors = registries.floors.keys.map { floorId -> floorId to byFloor[floorId].orEmpty() },
        unassignedAreas = withoutFloor.map { it.areaId },
    )
}
