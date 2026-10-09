package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.strategy.areasFloorHierarchy
import io.homeassistant.companion.android.dashboard.strategy.home.defaultIcon
import io.homeassistant.companion.android.dashboard.strategy.home.navigate
import io.homeassistant.companion.android.dashboard.strategy.home.putTapAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The layout the light, climate, security and maintenance view strategies share (frontend@20260624.6
// src/panels/{light,climate,security,maintenance}/strategies/*-view-strategy.ts): a section per floor, one for
// the areas without a floor, then one for the entities without an area. Upstream repeats it in each strategy.

/**
 * What differs between the summary views.
 *
 * @property filters which entities the view shows
 * @property areaCards the cards of one area (its heading included) for its entities; none to leave the area out
 * @property unassignedCard the card of an entity without an area
 * @property otherAreasKey the heading of the areas without a floor when there are floors
 * @property devicesKey the heading of the entities without an area when they are all the view shows
 * @property otherDevicesKey the heading of the entities without an area after other sections
 */
internal class SummaryViewSpec(
    val filters: List<EntityFilter>,
    val areaCards: HassSnapshot.(area: AreaEntry, entities: List<String>) -> List<JsonObject>,
    val unassignedCard: HassSnapshot.(entityId: String) -> JsonObject,
    val otherAreasKey: String,
    val devicesKey: String,
    val otherDevicesKey: String,
)

/** The sections of a summary view, and the entities it shows. */
internal data class SummarySections(val sections: List<JsonObject>, val entities: List<String>)

/** The sections of the summary view [spec] describes. */
internal fun HassSnapshot.summarySections(spec: SummaryViewSpec): SummarySections {
    val hierarchy = areasFloorHierarchy()
    val entities = findEntities(states.keys.toList(), spec.filters)
    val floorCount = hierarchy.floors.size + if (hierarchy.unassignedAreas.isNotEmpty()) 1 else 0
    val floorSections = hierarchy.floors.mapNotNull { (floorId, areaIds) ->
        val floor = registries.floors.getValue(floorId)
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", if (floorCount > 1) floor.name else localize(AREAS_KEY))
            put("icon", floor.icon?.ifEmpty { null } ?: floor.defaultIcon())
        }
        section(heading, areasCards(areaIds, entities, spec))
    }
    val otherAreasSection = hierarchy.unassignedAreas.takeIf { it.isNotEmpty() }?.let { areaIds ->
        val heading = plainHeading(localize(if (floorCount > 1) spec.otherAreasKey else AREAS_KEY))
        section(heading, areasCards(areaIds, entities, spec))
    }
    val areaSections = floorSections + listOfNotNull(otherAreasSection)
    val unassignedCards = filterEntities(entities, EntityFilter(areas = setOf(null))).map {
        spec.unassignedCard(this, it)
    }
    val unassignedSection = section(
        plainHeading(localize(if (areaSections.isNotEmpty()) spec.otherDevicesKey else spec.devicesKey)),
        unassignedCards,
    )
    return SummarySections(areaSections + listOfNotNull(unassignedSection), entities)
}

/** The cards of each of [areaIds] for its entities among [entities]. */
private fun HassSnapshot.areasCards(areaIds: List<String>, entities: List<String>, spec: SummaryViewSpec) =
    areaIds.mapNotNull { registries.areas[it] }.flatMap { area ->
        spec.areaCards(this, area, filterEntities(entities, EntityFilter(areas = setOf(area.areaId))))
    }

/** A full-width section of [heading] and [cards], or none without cards. */
private fun section(heading: JsonObject, cards: List<JsonObject>): JsonObject? = cards.takeIf { it.isNotEmpty() }?.let {
    buildJsonObject {
        put("type", "grid")
        put("column_span", SUMMARY_COLUMNS)
        put("cards", JsonArray(listOf(heading) + it))
    }
}

private fun plainHeading(text: String) = buildJsonObject {
    put("type", "heading")
    put("heading", text)
}

/** The heading of an area, linking to its view of the home dashboard when there is one. */
internal fun HassSnapshot.areaHeading(area: AreaEntry, badges: JsonArray? = null): JsonObject = buildJsonObject {
    put("heading_style", "subtitle")
    put("type", "heading")
    put("heading", area.name)
    if (HOME_PANEL in panels) putTapAction(navigate("/$HOME_PANEL/areas-${area.areaId}"))
    badges?.let { put("badges", it) }
}

/** A summary view of [sections]. */
internal fun summaryView(sections: List<JsonObject>, maxColumns: Int = SUMMARY_COLUMNS): JsonObject = buildJsonObject {
    put("type", "sections")
    put("max_columns", maxColumns)
    put("sections", JsonArray(sections))
}

/** Summary views have two columns, and their sections take both. */
private const val SUMMARY_COLUMNS = 2
private const val HOME_PANEL = "home"
internal const val AREAS_KEY = "ui.panel.lovelace.strategy.home.areas"
internal const val OTHER_AREAS_KEY = "ui.panel.lovelace.strategy.home.other_areas"
