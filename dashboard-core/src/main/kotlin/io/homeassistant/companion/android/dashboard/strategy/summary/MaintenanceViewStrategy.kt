package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.strategy.home.MAINTENANCE_FILTERS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The view of the maintenance panel: the batteries of each area by floor, then those without an area.
 *
 * Port of `MaintenanceViewStrategy.generate` (frontend@20260624.6
 * src/panels/maintenance/strategies/maintenance-view-strategy.ts). The strategy config is `{type: "maintenance"}`.
 */
fun HassSnapshot.maintenanceView(): JsonObject = summaryView(summarySections(MAINTENANCE_VIEW).sections)

private val MAINTENANCE_VIEW = SummaryViewSpec(
    filters = MAINTENANCE_FILTERS,
    areaCards = { area, entities ->
        if (entities.isEmpty()) emptyList() else listOf(areaHeading(area)) + entities.map { batteryTile(it) }
    },
    unassignedCard = { batteryTile(it) },
    // Upstream names the areas without a floor "other devices" here
    otherAreasKey = OTHER_DEVICES_KEY,
    devicesKey = "ui.panel.lovelace.strategy.maintenance.devices",
    otherDevicesKey = OTHER_DEVICES_KEY,
)

/** Port of `computeBatteryTileCard`: a battery named after its device, or itself without a device. */
private fun HassSnapshot.batteryTile(entityId: String): JsonObject = buildJsonObject {
    put("type", "tile")
    put("entity", entityId)
    putJsonObject("name") { put("type", if (registries.entities[entityId]?.deviceId != null) "device" else "entity") }
}

private const val OTHER_DEVICES_KEY = "ui.panel.lovelace.strategy.maintenance.other_devices"
