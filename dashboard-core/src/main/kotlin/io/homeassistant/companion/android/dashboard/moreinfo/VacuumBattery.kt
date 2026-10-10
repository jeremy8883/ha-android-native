package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string

// The battery of `more-info-vacuum`'s state header (frontend@20260624.6
// src/dialogs/more-info/controls/more-info-vacuum.ts `_renderBattery`) with `findBatteryEntity`
// (src/data/entity/entity_registry.ts).

/** A vacuum's battery: its level ("73%", none for a battery binary sensor) and icon. */
data class VacuumBattery(val text: String?, val icon: String?)

/**
 * The battery of the vacuum's device (a battery sensor first, then a battery binary sensor), else the deprecated
 * `battery_level` attribute; `null` with neither.
 */
internal fun HassSnapshot.vacuumBattery(state: EntityState): VacuumBattery? {
    val device = registries.entities[state.entityId]?.deviceId
    val battery = device?.let { deviceBattery(it) }
    if (battery != null) {
        val binary = battery.domain == BINARY_SENSOR
        val level = battery.state.toDoubleOrNull()
        if (binary || level != null) {
            return VacuumBattery(
                level?.takeUnless {
                    binary
                }?.let { "${Math.round(it)}%" },
                entityIcon(battery.entityId),
            )
        }
    }
    val level = state.attributes.numberOrNull("battery_level")?.takeIf { it != 0.0 }
    return level?.takeIf { state.supportsFeature(FEATURE_BATTERY) }?.let {
        VacuumBattery("${Math.round(it)}%", state.attributes.string("battery_icon"))
    }
}

/** Port of `findBatteryEntity`: the device's battery sensor, else its battery binary sensor. */
private fun HassSnapshot.deviceBattery(deviceId: String): EntityState? = registries.entities.values
    .filter { it.deviceId == deviceId }
    .mapNotNull { states[it.entityId] }
    .filter { it.attributes.string("device_class") == "battery" && it.domain in BATTERY_DOMAINS }
    .minByOrNull { BATTERY_DOMAINS.indexOf(it.domain) }

private const val BINARY_SENSOR = "binary_sensor"
private const val FEATURE_BATTERY = 64
private val BATTERY_DOMAINS = listOf("sensor", BINARY_SENSOR)
