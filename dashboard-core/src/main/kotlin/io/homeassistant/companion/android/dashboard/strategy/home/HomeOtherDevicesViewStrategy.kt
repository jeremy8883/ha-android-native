package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The view of devices without an area: one section per device with an entities card of its primary entities, or
 * an empty state when everything is assigned.
 *
 * Port of `HomeOtherDevicesViewStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/home/home-other-devices-view-strategy.ts). The strategy config is
 * `{type: "home-other-devices", home_panel?}`.
 */
fun HassSnapshot.homeOtherDevicesView(homePanel: Boolean): JsonObject {
    val byDevice = linkedMapOf<String, MutableList<String>>()
    for (entityId in findEntities(states.keys.toList(), OTHER_DEVICES_FILTERS)) {
        val device = registries.entityContext(entityId).device ?: continue
        byDevice.getOrPut(device.id) { mutableListOf() } += entityId
    }

    val primaryFilter = EntityFilter(entityCategories = setOf(ENTITY_CATEGORY_NONE))
    val sections = byDevice.mapNotNull { (deviceId, entityIds) ->
        val primary = filterEntities(entityIds, primaryFilter)
        if (primary.isEmpty()) null else deviceSection(deviceId, primary, homePanel)
    }.toMutableList()

    if (sections.isEmpty()) return allOrganizedView()
    // Take the full width with a single section to avoid a narrow header on desktop
    if (sections.size == 1) sections[0] = JsonObject(sections[0] + ("column_span" to JsonPrimitive(2)))

    return buildJsonObject {
        put("type", "sections")
        putJsonObject("header") { put("badges_position", "bottom") }
        // Between 2 and 3 columns; the max defines the width of the header
        put("max_columns", sections.size.coerceIn(2, MAX_COLUMNS))
        put("sections", JsonArray(sections))
    }
}

private fun HassSnapshot.deviceSection(deviceId: String, entityIds: List<String>, homePanel: Boolean): JsonObject {
    val device = registries.devices[deviceId]
    val isAdmin = user?.isAdmin == true
    val heading = buildJsonObject {
        put("type", "heading")
        put(
            "heading",
            device?.let {
                it.deviceName()?.ifEmpty { null }
                    ?: localize("ui.panel.lovelace.strategy.home.unnamed_device")
            }
                .orEmpty(),
        )
        if (device != null && isAdmin) {
            putTapAction(navigate("/config/devices/device/${device.id}"))
        } else {
            putJsonObject("tap_action") { put("action", "none") }
        }
        putJsonArray("badges") {
            if (homePanel && device != null && isAdmin) {
                addJsonObject {
                    put("type", "button")
                    put("icon", "mdi:home-plus")
                    put("text", localize("ui.panel.lovelace.strategy.home-other-devices.assign_area"))
                    putJsonObject("tap_action") {
                        put("action", "fire-dom-event")
                        putJsonObject("home_panel") {
                            put("type", "assign_area")
                            put("device_id", device.id)
                        }
                    }
                }
            }
        }
    }
    val entities = buildJsonObject {
        put("type", "entities")
        putJsonArray("entities") {
            entityIds.forEach { entityId ->
                addJsonObject {
                    put("entity", entityId)
                    putJsonObject("name") { put("type", "entity") }
                }
            }
        }
    }
    return gridSection(listOf(heading, entities))
}

private fun HassSnapshot.allOrganizedView(): JsonObject = buildJsonObject {
    put("type", "panel")
    putJsonArray("cards") {
        addJsonObject {
            put("type", "empty-state")
            put("icon", "mdi:check-all")
            put("icon_color", "primary")
            put("content_only", true)
            put("title", localize("ui.panel.lovelace.strategy.home-other-devices.all_organized_title"))
            put("content", localize("ui.panel.lovelace.strategy.home-other-devices.all_organized_content"))
        }
    }
}

/** The most columns the view has. */
private const val MAX_COLUMNS = 3
