package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.strategy.areasFloorHierarchy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The media players view of the home dashboard: one section per floor with a media control card per player,
 * grouped under area headings, then the players without an area.
 *
 * Port of `HomeMediaPlayersViewStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/home/home-media-players-view-strategy.ts).
 */
fun HassSnapshot.homeMediaPlayersView(): JsonObject {
    val hierarchy = areasFloorHierarchy()
    val players = findEntities(states.keys.toList(), HomeSummary.MEDIA_PLAYERS.filters)
    val floorCount = hierarchy.floors.size + if (hierarchy.unassignedAreas.isNotEmpty()) 1 else 0
    val areasLabel = localize("ui.panel.lovelace.strategy.home.areas")
    val sections = mutableListOf<JsonObject>()

    for ((floorId, areaIds) in hierarchy.floors) {
        val floor = registries.floors.getValue(floorId)
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", if (floorCount > 1) floor.name else areasLabel)
            put("icon", floor.icon?.ifEmpty { null } ?: floor.defaultIcon())
        }
        mediaSection(heading, areaMediaCards(areaIds, players))?.let { sections += it }
    }

    if (hierarchy.unassignedAreas.isNotEmpty()) {
        val heading = buildJsonObject {
            put("type", "heading")
            put(
                "heading",
                if (floorCount > 1) localize("ui.panel.lovelace.strategy.home.other_areas") else areasLabel,
            )
        }
        mediaSection(heading, areaMediaCards(hierarchy.unassignedAreas, players))?.let { sections += it }
    }

    val unassigned = filterEntities(players, EntityFilter(areas = setOf(null))).map(::mediaControlCard)
    if (unassigned.isNotEmpty()) {
        val key = if (sections.isNotEmpty()) "other_media_players" else "media_players"
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", localize("ui.panel.lovelace.strategy.home_media_players.$key"))
        }
        mediaSection(heading, unassigned)?.let { sections += it }
    }

    return buildJsonObject {
        put("type", "sections")
        put("max_columns", MEDIA_COLUMNS)
        put("sections", JsonArray(sections))
    }
}

/** A subtitle heading linking to the area view, then the area's players, for every area that has some. */
private fun HassSnapshot.areaMediaCards(areaIds: List<String>, players: List<String>): List<JsonObject> =
    areaIds.flatMap { areaId ->
        val area = registries.areas[areaId] ?: return@flatMap emptyList()
        val cards = filterEntities(players, EntityFilter(areas = setOf(areaId))).map(::mediaControlCard)
        if (cards.isEmpty()) return@flatMap emptyList()
        val heading = buildJsonObject {
            put("heading_style", "subtitle")
            put("type", "heading")
            put("heading", area.name)
            putTapAction(navigate("areas-${area.areaId}"))
        }
        listOf(heading) + cards
    }

private fun mediaSection(heading: JsonObject, cards: List<JsonObject>): JsonObject? =
    cards.takeIf { it.isNotEmpty() }?.let {
        buildJsonObject {
            put("type", "grid")
            put("column_span", MEDIA_COLUMNS)
            put("cards", JsonArray(listOf(heading) + it))
        }
    }

private fun mediaControlCard(entityId: String) = buildJsonObject {
    put("type", "media-control")
    put("entity", entityId)
}

private const val MEDIA_COLUMNS = 2
