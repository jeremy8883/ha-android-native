package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.strategy.areaTileCard
import io.homeassistant.companion.android.dashboard.strategy.home.SECURITY_FILTERS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The view of the security panel: cameras, alarms, locks, doors, windows and safety sensors of each area by
 * floor, then those without an area, with the activity of these entities and of people in a sidebar when the
 * logbook is loaded.
 *
 * Port of `SecurityViewStrategy.generate` (frontend@20260624.6
 * src/panels/security/strategies/security-view-strategy.ts). The strategy config is `{type: "security"}`.
 */
fun HassSnapshot.securityView(): JsonObject {
    val (sections, entities) = summarySections(SECURITY_VIEW)
    val view = summaryView(sections, maxColumns = SECURITY_COLUMNS)
    val sidebar = activitySidebar(entities) ?: return view
    return JsonObject(view + ("sidebar" to sidebar))
}

private val SECURITY_VIEW = SummaryViewSpec(
    filters = SECURITY_FILTERS,
    areaCards = { area, entities ->
        if (entities.isEmpty()) emptyList() else listOf(areaHeading(area)) + entities.map { securityTile(it) }
    },
    unassignedCard = { securityTile(it) },
    otherAreasKey = OTHER_AREAS_KEY,
    devicesKey = DEVICES_KEY,
    otherDevicesKey = "ui.panel.lovelace.strategy.security.other_devices",
)

private fun HassSnapshot.securityTile(entityId: String) = areaTileCard(entityId, prefix = "", includeFeature = false)

/** The last day's logbook of [entities] and of every person, when the logbook is loaded and there are any. */
private fun HassSnapshot.activitySidebar(entities: List<String>): JsonObject? {
    val people = states.keys.filter { it.substringBefore('.') == "person" }
    val logbookEntities = entities + people
    if (LOGBOOK !in config.components || logbookEntities.isEmpty()) return null
    val section = buildJsonObject {
        put("type", "grid")
        putJsonArray("cards") {
            addJsonObject {
                put("type", "heading")
                put("heading", localize(ACTIVITY_KEY))
                put("heading_style", "title")
            }
            addJsonObject {
                put("type", LOGBOOK)
                putJsonObject("target") { put("entity_id", JsonArray(logbookEntities.map(::JsonPrimitive))) }
                put("hours_to_show", ACTIVITY_HOURS)
                putJsonObject("grid_options") { put("columns", FULL_WIDTH_COLUMNS) }
            }
        }
    }
    return buildJsonObject {
        put("sections", JsonArray(listOf(section)))
        put("content_label", localize(DEVICES_KEY))
        put("sidebar_label", localize(ACTIVITY_KEY))
    }
}

/** The devices and the sidebar's activity make three columns on large screens. */
private const val SECURITY_COLUMNS = 3
private const val ACTIVITY_HOURS = 24
private const val FULL_WIDTH_COLUMNS = 12
private const val LOGBOOK = "logbook"
private const val DEVICES_KEY = "ui.panel.lovelace.strategy.security.devices"
private const val ACTIVITY_KEY = "ui.panel.lovelace.strategy.security.activity"
