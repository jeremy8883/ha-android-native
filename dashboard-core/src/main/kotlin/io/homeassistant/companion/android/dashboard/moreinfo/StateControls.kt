package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.attributeIcon
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.assumedState
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The large controls of the more-info dialog (frontend@20260624.6 src/state-control/, src/components/ha-control-*).

/**
 * A large slider, as a state control draws `ha-control-slider`.
 *
 * @property value the value, `null` when unknown (the bar is then empty)
 * @property mode how the value is drawn
 * @property inverted whether the value grows towards the start (the top, when vertical)
 * @property showHandle whether the bar ends in a handle, which keeps it visible at the minimum
 * @property color the bar's colour, `null` for the theme's primary colour
 * @property service the call that sets the value
 */
data class ControlSlider(
    val label: String,
    val value: Double?,
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: String?,
    val mode: SliderMode,
    val inverted: Boolean,
    val showHandle: Boolean,
    val enabled: Boolean,
    val color: DisplayColor?,
    val background: SliderBackground,
    val service: ValueService,
)

/** How a [ControlSlider] draws its value (`mode`). */
sealed interface SliderMode {
    /** A bar filled from the start up to the value. */
    data object Start : SliderMode

    /** A bar filled from the value to the end (a cover's position, open at the top). */
    data object End : SliderMode

    /** A cursor at the value, over the background. */
    data object Cursor : SliderMode
}

/** The track behind a [ControlSlider]'s value. */
sealed interface SliderBackground {
    /** A colour (`null` for the theme's disabled colour), at [opacity]. */
    data class Tint(val color: DisplayColor?, val opacity: Float) : SliderBackground

    /** A gradient of `#rrggbb` colours from the start (the top, when vertical), at their fractions of the length. */
    data class Gradient(val stops: List<Pair<Double, String>>) : SliderBackground

    /**
     * [color] at [opacity], under stripes of the slider's colour that widen towards the end (a cover's tilt,
     * `generateTiltSliderTrackBackgroundGradient`).
     */
    data class Stripes(val color: DisplayColor?, val opacity: Float) : SliderBackground
}

/**
 * The large on/off control (`ha-state-control-toggle`): a switch, or two buttons when the state isn't known for
 * sure (assumed or unknown).
 *
 * @property checked whether the switch is on (and the on button filled)
 * @property offActive whether the off button is filled (neither is while the state is unknown)
 * @property showHandle whether the switch's knob shows (while active)
 * @property buttons whether it shows as separate on and off buttons
 */
data class StateToggle(
    val label: String,
    val checked: Boolean,
    val offActive: Boolean,
    val showHandle: Boolean,
    val enabled: Boolean,
    val buttons: Boolean,
    val onColor: DisplayColor?,
    val offColor: DisplayColor?,
    val onIcon: String,
    val offIcon: String,
    val turnOnLabel: String,
    val turnOffLabel: String,
    val turnOn: CardAction.CallService,
    val turnOff: CardAction.CallService,
)

/**
 * A dropdown of choices (`ha-control-select-menu`).
 *
 * @property icon the menu's icon, shown while nothing is selected, or always when its options have no icons
 * @property value the selected option's value, `null` when none
 * @property optionIcons whether the options have icons, the selected one's then replacing [icon] (even when it has
 * none, as upstream renders it)
 */
data class SelectMenu(
    val label: String,
    val icon: String,
    val value: String?,
    val enabled: Boolean,
    val options: List<MenuOption>,
    val optionIcons: Boolean = true,
)

/** One choice of a [SelectMenu], with its [icon] when it has one. */
data class MenuOption(val value: String, val label: String, val icon: String?, val action: CardAction.CallService)

/** Port of `ha-state-control-toggle` for [state], with [onIcon] and [offIcon] for its two sides. */
fun HassSnapshot.stateToggle(state: EntityState, onIcon: String, offIcon: String): StateToggle {
    // Groups turn on and off through the homeassistant domain
    val domain = if (state.domain == "group") "homeassistant" else state.domain
    return StateToggle(
        label = localize("ui.card.common.toggle"),
        checked = state.state == ON,
        offActive = state.state == OFF,
        showHandle = state.isActive(),
        enabled = state.available(),
        buttons = state.assumedState() || state.state == UNKNOWN,
        onColor = stateColor(state, ON),
        offColor = stateColor(state, OFF),
        onIcon = onIcon,
        offIcon = offIcon,
        turnOnLabel = localize("ui.card.common.turn_on"),
        turnOffLabel = localize("ui.card.common.turn_off"),
        turnOn = CardAction.CallService(domain, "turn_on", entityData(state), target = null),
        turnOff = CardAction.CallService(domain, "turn_off", entityData(state), target = null),
    )
}

/**
 * A menu of [state]'s [attribute], listing the values of [listAttribute] with their translated names and icons,
 * each set by `[domain].[service]` with `{attribute: value}`; `null` without the list. Ports the more-info
 * dialogs' `ha-control-select-menu`s of an attribute (presets, fan modes and so on).
 */
fun HassSnapshot.attributeMenu(
    state: EntityState,
    attribute: String,
    listAttribute: String,
    service: String,
    icon: String,
): SelectMenu? {
    val values = (state.attributes[listAttribute] as? JsonArray)?.mapNotNull { it.stringOrNull } ?: return null
    return SelectMenu(
        label = attributeName(state, attribute),
        icon = icon,
        value = state.attributes.string(attribute),
        enabled = state.available(),
        options = values.map { value ->
            MenuOption(
                value = value,
                label = formatEntityAttributeValue(state, attribute, JsonPrimitive(value)),
                icon = attributeIcon(state, attribute, value),
                action = CardAction.CallService(
                    state.domain,
                    service,
                    JsonObject(mapOf(attribute to JsonPrimitive(value)) + entityData(state)),
                    target = null,
                ),
            )
        },
    )
}

private const val ON = "on"
private const val OFF = "off"
private const val UNKNOWN = "unknown"
