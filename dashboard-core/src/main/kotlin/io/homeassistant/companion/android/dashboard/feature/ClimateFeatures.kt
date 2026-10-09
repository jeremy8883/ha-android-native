package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The target temperature, or the low and high targets of a climate entity that has a range. */
internal fun HassSnapshot.targetTemperature(state: EntityState): TileFeature? {
    val target = hasTargetTemperature(state)
    val range = state.domain == "climate" && state.supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE_RANGE)
    if (!target && !range) return null
    val items = TemperatureItems(this, state)
    val temperature = number(state.attributes["temperature"])
    val low = number(state.attributes["target_temp_low"])
    val high = number(state.attributes["target_temp_high"])
    val available = state.available()
    return TileFeature.NumberButtons(
        when {
            target && temperature != null && available -> items.target(temperature)
            range && low != null && high != null && available -> items.range(low, high)
            else -> items.unknown()
        },
    )
}

private fun hasTargetTemperature(state: EntityState): Boolean = when (state.domain) {
    "climate" -> state.supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE)
    "water_heater" -> state.supportsFeature(EntityFeature.WATER_HEATER_TARGET_TEMPERATURE)
    else -> false
}

/** The temperature controls of [state], with its step, limits and colour. */
private class TemperatureItems(private val hass: HassSnapshot, private val state: EntityState) {
    private val step = number(state.attributes["target_temp_step"])?.takeIf { it != 0.0 }
        ?: if (hass.config.temperatureUnit == UNIT_F) 1.0 else HALF_DEGREE
    private val min = number(state.attributes["min_temp"])
    private val max = number(state.attributes["max_temp"])
    private val color = stateColor(state)

    fun target(temperature: Double) =
        listOf(item("temperature", temperature, min, max, service(entityData(state), "temperature")))

    fun range(low: Double, high: Double) = listOf(
        item(
            "target_temp_low",
            low,
            min,
            minOf(max ?: high, high),
            service(rangeData(state, "target_temp_high", high), "target_temp_low"),
        ),
        item(
            "target_temp_high",
            high,
            maxOf(min ?: low, low),
            max,
            service(rangeData(state, "target_temp_low", low), "target_temp_high"),
        ),
    )

    fun unknown() = listOf(item("temperature", null, null, null, null))

    private fun service(data: JsonObject, valueKey: String) =
        ValueService(state.domain, "set_temperature", data, valueKey)

    private fun item(attribute: String, value: Double?, low: Double?, high: Double?, service: ValueService?) =
        TileFeature.NumberItem(
            label = hass.attributeName(state, attribute),
            value = value,
            min = low,
            max = high,
            step = step,
            fractionDigits = jsNumberString(step).substringAfter('.', "").length,
            unit = hass.config.temperatureUnit,
            service = service,
            color = color,
        )
}

/** Range changes send both ends, like upstream's `_callService` for `low`/`high`. */
private fun rangeData(state: EntityState, otherKey: String, otherValue: Double) =
    JsonObject(entityData(state) + (otherKey to JsonPrimitive(otherValue)))

private const val HALF_DEGREE = 0.5
private const val UNIT_F = "°F"
