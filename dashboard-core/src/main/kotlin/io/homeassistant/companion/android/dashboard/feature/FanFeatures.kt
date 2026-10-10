package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot

internal fun HassSnapshot.fanSpeed(state: EntityState): TileFeature? {
    if (state.domain != "fan" || !state.supportsFeature(EntityFeature.FAN_SET_SPEED)) return null
    val step = number(state.attributes["percentage_step"]) ?: 1.0
    val speedCount = Math.round(PERCENT / step).toInt() + 1
    val percentage = if (state.isActive()) number(state.attributes["percentage"]) ?: 0.0 else 0.0
    return FAN_SPEEDS[speedCount]?.let { fanSpeedSelect(state, it, step, percentage) }
        ?: fanSpeedSlider(state, step, percentage)
}

/** Named speeds, for fans with few of them. */
private fun HassSnapshot.fanSpeedSelect(
    state: EntityState,
    speeds: List<String>,
    step: Double,
    percentage: Double,
): TileFeature {
    fun call(percent: Double) =
        ValueService("fan", "set_percentage", entityData(state), "percentage").withValue(percent)
    return TileFeature.Select(
        label = attributeName(state, "percentage"),
        options = speeds.mapIndexed { index, speed ->
            TileFeature.Option(
                value = speed,
                label = fanSpeedLabel(state, speed),
                icon = fanSpeedIcon(speed, index),
                action = call(Math.floor(index * step)),
            )
        },
        selected = speeds.getOrElse(Math.round(percentage / step).toInt()) { "off" },
        enabled = state.available(),
        color = null,
    )
}

/** The name of [speed]: the state for on and off, else the card's speed name. */
internal fun HassSnapshot.fanSpeedLabel(state: EntityState, speed: String): String = if (speed == "on" ||
    speed == "off"
) {
    formatEntityState(state, speed)
} else {
    localize("ui.card.fan.speed.$speed").ifEmpty { speed }
}

/** A percentage slider, for fans with many speeds. */
private fun HassSnapshot.fanSpeedSlider(state: EntityState, step: Double, percentage: Double): TileFeature =
    TileFeature.Slider(
        label = attributeName(state, "percentage"),
        value = maxOf(Math.round(percentage).toInt(), 0),
        min = 0,
        max = PERCENT.toInt(),
        step = maxOf(step.toInt(), 1),
        unit = "%",
        showHandle = true,
        enabled = state.available(),
        service = ValueService("fan", "set_percentage", entityData(state), "percentage"),
    )

/** Port of `computeFanSpeedIcon` (src/data/fan.ts). */
internal fun fanSpeedIcon(speed: String, index: Int) = when (speed) {
    "on" -> "mdi:fan"
    "off" -> "mdi:fan-off"
    else -> FAN_SPEED_ICONS.getOrElse(index - 1) { "mdi:fan" }
}

/** Port of `FAN_SPEEDS` (src/data/fan.ts): speed names by speed count, for counts up to 4. */
internal val FAN_SPEEDS = mapOf(
    2 to listOf("off", "on"),
    3 to listOf("off", "low", "high"),
    4 to listOf("off", "low", "medium", "high"),
)
private val FAN_SPEED_ICONS = listOf("mdi:fan-speed-1", "mdi:fan-speed-2", "mdi:fan-speed-3")
