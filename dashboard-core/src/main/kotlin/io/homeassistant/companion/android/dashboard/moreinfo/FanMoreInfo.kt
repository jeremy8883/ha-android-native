package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.FAN_SPEEDS
import io.homeassistant.companion.android.dashboard.feature.PERCENT
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.feature.fanSpeedIcon
import io.homeassistant.companion.android.dashboard.feature.fanSpeedLabel

// Port of `more-info-fan` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-fan.ts) with
// `ha-state-control-fan-speed` (src/state-control/fan/) and its helpers (src/data/fan.ts).

/**
 * What a fan's details show: the state with the speed ("67%"), the speed as named buttons or a slider (with a
 * power button beside the slider), or a switch for a fan without speeds, and its preset, direction and oscillation
 * menus.
 *
 * @property power the call of the power button beside the slider, `null` without the slider
 */
data class FanMoreInfo(
    val state: String,
    val speed: FanSpeedControl?,
    val toggle: StateToggle?,
    val power: CardAction.CallService?,
    val powerEnabled: Boolean,
    val menus: List<SelectMenu>,
)

/** How a fan's speed is set. */
sealed interface FanSpeedControl {
    /** Named speeds, the fastest at the top, filled in [color] when chosen. */
    data class Buttons(
        val label: String,
        val value: String,
        val enabled: Boolean,
        val color: DisplayColor?,
        val options: List<MenuOption>,
    ) : FanSpeedControl

    /** A percentage slider. */
    data class Slider(val slider: ControlSlider) : FanSpeedControl
}

/** The details of a fan, or `null` for another entity. */
fun HassSnapshot.fanMoreInfo(state: EntityState): FanMoreInfo? {
    if (state.domain != FAN) return null
    val speeds = state.supportsFeature(EntityFeature.FAN_SET_SPEED)
    val named = FAN_SPEEDS[speedCount(state)]
    val slider = speeds && named == null
    return FanMoreInfo(
        state = fanStateDisplay(state),
        speed = when {
            !speeds -> null
            named != null -> speedButtons(state, named)
            else -> FanSpeedControl.Slider(speedSlider(state))
        },
        toggle = if (speeds) null else stateToggle(state, "mdi:fan", "mdi:fan-off"),
        power = if (slider) powerCall(state) else null,
        powerEnabled = state.available(),
        menus = listOfNotNull(presetMenu(state), directionMenu(state), oscillatingMenu(state)),
    )
}

/** The state header: the speed while on at some speed (`computeFanSpeedStateDisplay`), else the state. */
private fun HassSnapshot.fanStateDisplay(state: EntityState): String {
    val percentage = state.attributes.numberOrNull(PERCENTAGE)?.takeIf { state.isActive() && it != 0.0 }
    return percentage?.let { formatEntityAttributeValue(state, PERCENTAGE, jsonNumber(Math.round(it).toDouble())) }
        ?: formatEntityState(state)
}

private fun step(state: EntityState) = state.attributes.numberOrNull("percentage_step") ?: 1.0

/** Port of `computeFanSpeedCount`. */
private fun speedCount(state: EntityState) = Math.round(PERCENT / step(state)).toInt() + 1

/** The speed shown: the percentage while on, else none. */
private fun percentage(state: EntityState) =
    if (state.isActive()) state.attributes.numberOrNull(PERCENTAGE) ?: 0.0 else 0.0

private fun HassSnapshot.speedButtons(state: EntityState, speeds: List<String>): FanSpeedControl.Buttons {
    val step = step(state)
    val call = ValueService(FAN, "set_percentage", entityData(state), PERCENTAGE)
    return FanSpeedControl.Buttons(
        label = attributeName(state, PERCENTAGE),
        // Port of `fanPercentageToSpeed`
        value = speeds.getOrElse(Math.round(percentage(state) / step).toInt()) { "off" },
        enabled = state.available(),
        color = stateColor(state),
        options = speeds.mapIndexed { index, speed ->
            MenuOption(
                speed,
                fanSpeedLabel(state, speed),
                fanSpeedIcon(speed, index),
                call.withValue(
                    Math.floor(
                        index * step,
                    ),
                ),
            )
        }.reversed(),
    )
}

private fun HassSnapshot.speedSlider(state: EntityState): ControlSlider {
    val color = stateColor(state)
    return ControlSlider(
        label = attributeName(state, PERCENTAGE),
        value = maxOf(Math.round(percentage(state)).toDouble(), 0.0),
        min = 0.0,
        max = PERCENT,
        step = step(state),
        unit = "%",
        mode = SliderMode.Start,
        inverted = false,
        showHandle = false,
        enabled = state.available(),
        color = color,
        background = SliderBackground.Tint(color, BACKGROUND_OPACITY),
        service = ValueService(FAN, "set_percentage", entityData(state), PERCENTAGE),
    )
}

/** The power button turns the fan off while it's on, else on. */
private fun powerCall(state: EntityState) =
    CardAction.CallService(FAN, if (state.state == ON) "turn_off" else "turn_on", entityData(state), target = null)

private const val FAN = "fan"
private const val ON = "on"
private const val PERCENTAGE = "percentage"
private const val BACKGROUND_OPACITY = 0.2f
