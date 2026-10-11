package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import kotlinx.serialization.json.JsonPrimitive

// How a history's lines are grouped into charts: by unit, and by device class when split. Ports of
// `computeGroupKey` and of `computeHistory`'s device class and numeric history lookups (frontend@20260624.6
// src/data/history.ts).

/** Port of `computeGroupKey`: the unit, with the device class when charts are split by it. */
internal fun groupKey(unit: String, deviceClass: String?, splitDeviceClasses: Boolean): String =
    if (splitDeviceClasses) "${unit}_${deviceClass.orEmpty()}" else unit

/** The device class lines are grouped by: the domain's own, else the entity's (or its history's). */
internal fun HassSnapshot.historyDeviceClass(id: String, states: List<HistoryState>): String? {
    val domain = id.substringBefore('.')
    SPECIAL_DOMAIN_CLASSES[domain]?.let { return it }
    val current = this.states[id]
    val attributes = current?.attributes ?: numericStateFromHistory(domain, current, states)?.attributes
    return (attributes?.get("device_class") as? JsonPrimitive)?.takeIf(::jsTruthy)?.content
}

/** The first state of the history with numeric attributes, read when the entity is gone and not numeric by domain. */
internal fun numericStateFromHistory(domain: String, current: EntityState?, states: List<HistoryState>): HistoryState? =
    if (current != null || domain in NUMERICAL_DOMAINS) {
        null
    } else {
        states.firstOrNull { it.attributes?.let(::isNumericFromAttributes) == true }
    }

/** Port of `SPECIAL_DOMAIN_CLASSES`: the device class of domains that chart a kind of value. */
private val SPECIAL_DOMAIN_CLASSES = mapOf(
    "climate" to "temperature",
    "humidifier" to "humidity",
    "water_heater" to "temperature",
)
