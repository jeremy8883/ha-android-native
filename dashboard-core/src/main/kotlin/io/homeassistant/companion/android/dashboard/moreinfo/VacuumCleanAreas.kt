package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.strategy.home.defaultIcon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `ha-more-info-view-vacuum-clean-areas` (frontend@20260624.6 src/dialogs/more-info/components/vacuum/)
// with `getAreasFloorHierarchy` (src/common/areas/areas-floor-hierarchy.ts). Not ported: mapping the vacuum's
// segments to areas (an admin's setting), which "Configure" leaves to the full dialog.

/**
 * Cleaning a vacuum by area: the areas mapped to its segments (in its registry entry), by floor, chosen in order,
 * then cleaned with `vacuum.clean_area`. Without mapped areas, it says so ([empty]).
 *
 * @property sections the mapped areas by floor (then those without one), labelled when there are several
 */
data class VacuumCleanAreas(
    val title: String,
    val sections: List<CleanAreaSection>,
    val empty: CleanAreasEmpty?,
    val hint: String,
    val startLabel: String,
    private val entityId: String,
) {
    /** The call cleaning [areaIds], in the order chosen. */
    fun clean(areaIds: List<String>): CardAction.CallService = CardAction.CallService(
        "vacuum",
        "clean_area",
        JsonObject(
            mapOf(
                "entity_id" to JsonPrimitive(entityId),
                "cleaning_area_id" to JsonArray(areaIds.map(::JsonPrimitive)),
            ),
        ),
        target = null,
    )
}

/** One floor's mapped areas (or those without a floor), under its [label] and [icon] when sections are labelled. */
data class CleanAreaSection(val label: String?, val icon: String?, val areas: List<CleanArea>)

/** An area to clean: its name and icon (a textured box without one). */
data class CleanArea(val areaId: String, val name: String, val icon: String)

/** What shows without mapped areas: why, and for an admin, that mapping them is in the full dialog. */
data class CleanAreasEmpty(val title: String, val text: String, val configureLabel: String?)

/** The clean-by-area view of [state] given its registry [entry]; `null` for another entity. */
fun HassSnapshot.vacuumCleanAreas(state: EntityState, entry: JsonObject?): VacuumCleanAreas? {
    if (state.domain != "vacuum") return null
    val mapped = entry?.obj("options")?.obj("vacuum")?.obj("area_mapping")?.keys.orEmpty()
    val areas = registries.areas.values.filter { it.areaId in mapped }
    val byFloor = registries.floors.values.mapNotNull { floor ->
        areas.filter { it.floorId == floor.floorId }.takeIf { it.isNotEmpty() }?.let { floor to it }
    }
    val unassigned = areas.filter { it.floorId == null || it.floorId !in registries.floors }
    val labelled = byFloor.size + (if (unassigned.isEmpty()) 0 else 1) > 1
    val area = { a: AreaEntry ->
        CleanArea(a.areaId, a.name.trim(), a.icon ?: "mdi:texture-box")
    }
    val sections = byFloor.map { (floor, floorAreas) ->
        CleanAreaSection(
            floor.name.takeIf {
                labelled
            },
            (floor.icon ?: floor.defaultIcon()).takeIf { labelled },
            floorAreas.map(area),
        )
    } + listOfNotNull(
        unassigned.takeIf { it.isNotEmpty() }?.let {
            CleanAreaSection(localize("$STRINGS.other_areas").takeIf { labelled }, null, it.map(area))
        },
    )
    val admin = user?.isAdmin == true
    return VacuumCleanAreas(
        title = "${localize("$STRINGS.cleaning")} · ${localize("$STRINGS.by_area")}",
        sections = sections,
        empty = if (sections.isEmpty()) {
            CleanAreasEmpty(
                title = localize("$STRINGS.no_areas_header"),
                text = localize(if (admin) "$STRINGS.no_areas_text" else "$STRINGS.no_areas_text_non_admin"),
                configureLabel = localize("$STRINGS.configure").takeIf { admin },
            )
        } else {
            null
        },
        hint = localize("$STRINGS.clean_areas_order_hint"),
        startLabel = localize("$STRINGS.start_cleaning_areas"),
        entityId = state.entityId,
    )
}

private const val STRINGS = "ui.dialogs.more_info_control.vacuum"
