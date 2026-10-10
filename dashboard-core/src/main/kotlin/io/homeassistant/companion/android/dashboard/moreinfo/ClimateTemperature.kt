package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

// Port of `ha-state-control-climate-temperature` (frontend@20260624.6 src/state-control/climate/).

/** The targets a thermostat has, as `ha-state-control-climate-temperature` reads them. */
fun climateTargets(state: EntityState): CircularTargets = CircularTargets(
    value = state.attributes.numberOrNull("temperature"),
    low = state.attributes.numberOrNull("target_temp_low"),
    high = state.attributes.numberOrNull("target_temp_high"),
)

/** The call that sets [targets]: the [range]'s ends together, or the single target. Port of `_callService`. */
fun climateTemperatureCall(state: EntityState, targets: CircularTargets, range: Boolean): CardAction.CallService {
    val data = if (range) {
        mapOf("target_temp_low" to jsonNumber(targets.low), "target_temp_high" to jsonNumber(targets.high))
    } else {
        mapOf("temperature" to jsonNumber(targets.value))
    }
    return CardAction.CallService(CLIMATE, "set_temperature", JsonObject(entityData(state) + data), target = null)
}

/** The temperature step: the entity's, else 1° in Fahrenheit and 0.5° otherwise. */
fun HassSnapshot.climateTemperatureStep(state: EntityState): Double =
    state.attributes.numberOrNull("target_temp_step")?.takeIf { it != 0.0 }
        ?: if (config.temperatureUnit == UNIT_F) 1.0 else HALF_DEGREE

/** Which of upstream's three renders the dial is: a single target, a range, or the current reading only. */
private class TemperatureRender(state: EntityState, targets: CircularTargets) {
    val unavailable = state.state == UNAVAILABLE
    val target = state.supportsFeature(FEATURE_TARGET_TEMPERATURE) && targets.value != null
    val range = state.supportsFeature(FEATURE_TARGET_TEMPERATURE_RANGE) && targets.low != null && targets.high != null
    val interactive = (target || range) && !unavailable
    val dual = interactive && !target
}

/** The temperature dial of [state]. */
internal fun HassSnapshot.climateTemperature(state: EntityState): CircularControl {
    val targets = climateTargets(state)
    val render = TemperatureRender(state, targets)
    val step = climateTemperatureStep(state)
    val active = state.isActive()
    return CircularControl(
        slider = temperatureSlider(state, targets, render, step),
        label = climateLabel(state, render.target || render.range),
        labelDisabled = render.unavailable,
        primary = temperaturePrimary(
            state,
            targets,
            render,
            BigNumber(0.0, config.temperatureUnit.orEmpty(), fractionDigits(step)),
        ),
        buttons = render.interactive,
        lowButtonColor = stateColor(state, "heat").takeIf { render.dual && active },
        highButtonColor = stateColor(state, "cool").takeIf { render.dual && active },
    )
}

private fun temperatureSlider(
    state: EntityState,
    targets: CircularTargets,
    render: TemperatureRender,
    step: Double,
): CircularSlider {
    val active = state.isActive()
    return CircularSlider(
        mode = when {
            render.interactive && render.target -> sliderMode(state)
            render.dual -> CircularMode.Start
            else -> CircularMode.Full
        },
        dual = render.dual,
        value = targets.value.takeIf { render.interactive && render.target },
        low = targets.low.takeIf { render.dual },
        high = targets.high.takeIf { render.dual },
        current = state.attributes.numberOrNull(CURRENT_TEMPERATURE),
        min = state.attributes.numberOrNull("min_temp") ?: DEFAULT_MIN,
        max = state.attributes.numberOrNull("max_temp") ?: DEFAULT_MAX,
        step = step,
        inactive = render.interactive && !active,
        readonly = !render.interactive,
        disabled = !render.interactive && !active,
        color = stateColor(state).takeIf { !render.dual },
        lowColor = stateColor(state, if (active) "heat" else OFF).takeIf { render.dual },
        highColor = stateColor(state, if (active) "cool" else OFF).takeIf { render.dual },
        actionColor = actionColor(state, active),
    )
}

/** Port of `_renderPrimary`: the target, the range, else the state while available. */
private fun HassSnapshot.temperaturePrimary(
    state: EntityState,
    targets: CircularTargets,
    render: TemperatureRender,
    number: BigNumber,
): CircularPrimary {
    val value = targets.value
    val low = targets.low
    val high = targets.high
    return when {
        render.target && value != null -> CircularPrimary.Target(number.copy(value = value))
        render.range && low != null && high != null ->
            CircularPrimary.Range(number.copy(value = low), number.copy(value = high))
        !render.unavailable -> CircularPrimary.Text(formatEntityState(state))
        else -> CircularPrimary.None
    }
}

/** Port of `_renderLabel`: unavailable (greyed), else the action, else the state when a target shows. */
private fun HassSnapshot.climateLabel(state: EntityState, showsTarget: Boolean): String? {
    val action = state.attributes.string("hvac_action")
    return when {
        state.state == UNAVAILABLE -> formatEntityState(state, UNAVAILABLE)
        action != null && action != OFF -> formatEntityAttributeValue(state, "hvac_action")
        showsTarget -> formatEntityState(state)
        else -> null
    }
}

/** The glow of what the thermostat is doing, while it heats, cools and so on. */
private fun actionColor(state: EntityState, active: Boolean): DisplayColor? {
    val action = state.attributes.string("hvac_action")?.takeIf { it != "idle" && it != OFF && active }
    return action?.let { HVAC_ACTION_TO_MODE[it]?.let { mode -> stateColor(state, mode) } ?: stateColor(state, null) }
}

/** `SLIDER_MODES` for the mode, or for the one heating or cooling mode of a thermostat that is off or on auto. */
private fun sliderMode(state: EntityState): CircularMode {
    val heatCool = state.attributes.stringList("hvac_modes").filter { it in HEAT_COOL_MODES }
    val mode = if (heatCool.size == 1 && state.state in setOf(OFF, "auto")) heatCool.single() else state.state
    return when (mode) {
        "heat" -> CircularMode.Start
        "cool" -> CircularMode.End
        in FULL_MODES -> CircularMode.Full
        else -> CircularMode.Start
    }
}

private const val OFF = "off"
private const val UNAVAILABLE = "unavailable"
private const val UNIT_F = "°F"
private const val HALF_DEGREE = 0.5
private const val DEFAULT_MIN = 7.0
private const val DEFAULT_MAX = 35.0
private const val FEATURE_TARGET_TEMPERATURE = 1
private const val FEATURE_TARGET_TEMPERATURE_RANGE = 2
private val HEAT_COOL_MODES = setOf("heat", "cool", "heat_cool")
private val FULL_MODES = setOf("auto", "dry", "fan_only", "heat_cool", OFF)

/** `CLIMATE_HVAC_ACTION_TO_MODE`. */
private val HVAC_ACTION_TO_MODE = mapOf(
    "cooling" to "cool",
    "defrosting" to "heat",
    "drying" to "dry",
    "fan" to "fan_only",
    "heating" to "heat",
    "idle" to OFF,
    OFF to OFF,
    "preheating" to "heat",
)
