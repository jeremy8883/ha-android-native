package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string

// The battery in the state header of `more-info-vacuum` and `more-info-lawn_mower` (frontend@20260624.6
// src/dialogs/more-info/controls/, `_renderBattery`) with `findBatteryEntity` (src/data/entity/entity_registry.ts).

/** A device's battery: its level ("73%", none for a battery binary sensor) and icon. */
data class DeviceBattery(val text: String?, val icon: String?)

/** The battery of [state]'s device: a battery sensor first, then a battery binary sensor; `null` without. */
internal fun HassSnapshot.deviceBattery(state: EntityState): DeviceBattery? {
    val battery = registries.entities[state.entityId]?.deviceId?.let { batteryEntity(it) } ?: return null
    val binary = battery.domain == BINARY_SENSOR
    val level = battery.state.toDoubleOrNull()
    return if (binary || level != null) {
        DeviceBattery(level?.takeUnless { binary }?.let { "${Math.round(it)}%" }, entityIcon(battery.entityId))
    } else {
        null
    }
}

/** A vacuum's battery: its device's, else the old `battery_level` and `battery_icon` attributes. */
internal fun HassSnapshot.vacuumBattery(state: EntityState): DeviceBattery? = deviceBattery(state)
    ?: state.attributes.numberOrNull("battery_level")?.takeIf { it != 0.0 && state.supportsFeature(FEATURE_BATTERY) }
        ?.let { DeviceBattery("${Math.round(it)}%", state.attributes.string("battery_icon")) }

/** Port of `findBatteryEntity`: the device's battery sensor, else its battery binary sensor. */
private fun HassSnapshot.batteryEntity(deviceId: String): EntityState? = registries.entities.values
    .filter { it.deviceId == deviceId }
    .mapNotNull { states[it.entityId] }
    .filter { it.attributes.string("device_class") == "battery" && it.domain in BATTERY_DOMAINS }
    .minByOrNull { BATTERY_DOMAINS.indexOf(it.domain) }

private const val BINARY_SENSOR = "binary_sensor"
private const val FEATURE_BATTERY = 64
private val BATTERY_DOMAINS = listOf("sensor", BINARY_SENSOR)
