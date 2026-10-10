package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.color.DEFAULT_MAX_KELVIN
import io.homeassistant.companion.android.dashboard.color.DEFAULT_MIN_KELVIN
import io.homeassistant.companion.android.dashboard.color.rgb2hex
import io.homeassistant.companion.android.dashboard.color.temperature2rgb
import io.homeassistant.companion.android.dashboard.derive.attributeIcon
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.lightColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `more-info-light` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-light.ts) with
// `ha-state-control-light-brightness` (src/state-control/light/) and `light-color-temp-picker`
// (src/dialogs/more-info/components/lights/), and the light helpers of src/data/light.ts.

/**
 * What a light's details show.
 *
 * @property state the state header's text in place of the state: the brightness, while the light has one
 * @property toggle the on/off switch, for a light without brightness
 * @property brightness the brightness slider, the main control
 * @property colorTemp the colour temperature slider, shown in place of the brightness when chosen
 * @property color whether the light takes colours, which the colour picker sets in place of the brightness
 * @property buttons the row of buttons under the main control, empty when there is none
 * @property effect the effect menu
 */
data class LightMoreInfo(
    val state: String?,
    val toggle: StateToggle?,
    val brightness: ControlSlider?,
    val colorTemp: ControlSlider?,
    val color: Boolean,
    val buttons: List<LightButton>,
    val effect: SelectMenu?,
)

/** Which control a light's details show in the middle. */
sealed interface LightMainControl {
    /** The brightness slider. */
    data object Brightness : LightMainControl

    /** The colour picker. */
    data object Color : LightMainControl

    /** The colour temperature slider. */
    data object ColorTemp : LightMainControl
}

/** One of the buttons under a light's main control (`ha-icon-button-group`). */
sealed interface LightButton {
    /** Turns the light on or off. */
    data class Power(val label: String, val enabled: Boolean, val action: CardAction.CallService) : LightButton

    /** Shows [control] in the middle: an icon for the brightness, a swatch for colours. */
    data class Show(val label: String, val enabled: Boolean, val control: LightMainControl) : LightButton

    /** Sets the light to white. */
    data class White(val label: String, val enabled: Boolean, val action: CardAction.CallService) : LightButton

    /** A divider between groups of buttons. */
    data object Separator : LightButton
}

/** The details of a light, or `null` for another entity. */
fun HassSnapshot.lightMoreInfo(state: EntityState): LightMoreInfo? {
    if (state.domain != LIGHT) return null
    val modes = state.attributes.array("supported_color_modes")?.mapNotNull { it.stringOrNull }.orEmpty().toSet()
    val supportsBrightness = modes.any { it in BRIGHTNESS_MODES }
    val supportsColor = modes.any { it in COLOR_MODES }
    val supportsColorTemp = COLOR_TEMP in modes
    val brightness = state.attributes["brightness"]?.takeIf { it !is JsonNull }?.let(::jsNumber)
    return LightMoreInfo(
        state = brightness?.takeIf { it != 0.0 }?.let { formatEntityAttributeValue(state, "brightness") },
        toggle = if (supportsBrightness) null else stateToggle(state, "mdi:lightbulb-on", "mdi:lightbulb-off"),
        brightness = if (supportsBrightness) brightnessSlider(state, brightness) else null,
        colorTemp = if (supportsColorTemp) colorTempSlider(state) else null,
        color = supportsColor,
        buttons = if (supportsBrightness) lightButtons(state, modes, supportsColor, supportsColorTemp) else emptyList(),
        effect = effectMenu(state),
    )
}

/** Port of `ha-state-control-light-brightness`: 1–100 %, in the light's colour. */
private fun HassSnapshot.brightnessSlider(state: EntityState, brightness: Double?) = ControlSlider(
    label = attributeName(state, "brightness"),
    value = brightness?.let { maxOf(Math.round(it * PERCENT / BRIGHTNESS_MAX).toDouble(), 1.0) },
    min = 1.0,
    max = PERCENT,
    step = 1.0,
    unit = "%",
    mode = SliderMode.Start,
    inverted = false,
    showHandle = state.isActive(),
    enabled = state.available(),
    color = lightColor(state) ?: stateColor(state),
    background = SliderBackground.Tint(lightColor(state) ?: stateColor(state), BACKGROUND_OPACITY),
    service = ValueService(LIGHT, TURN_ON, entityData(state), "brightness_pct"),
)

/** Port of `light-color-temp-picker`: a cursor over the light's range of whites, warmest at the top. */
private fun HassSnapshot.colorTempSlider(state: EntityState): ControlSlider {
    val min = state.attributes["min_color_temp_kelvin"]?.takeIf { it !is JsonNull }?.let(::jsNumber)
        ?: DEFAULT_MIN_KELVIN
    val max = state.attributes["max_color_temp_kelvin"]?.takeIf { it !is JsonNull }?.let(::jsNumber)
        ?: DEFAULT_MAX_KELVIN
    val inColorTemp = state.state == ON && state.attributes.string("color_mode") == COLOR_TEMP
    return ControlSlider(
        label = localize("$LIGHT_STRINGS.color_temp"),
        value = if (inColorTemp) {
            state.attributes["color_temp_kelvin"]?.takeIf {
                it !is JsonNull
            }?.let(::jsNumber)
        } else {
            null
        },
        min = min,
        max = max,
        step = 1.0,
        unit = "K",
        mode = SliderMode.Cursor,
        inverted = true,
        showHandle = false,
        enabled = state.available(),
        color = stateColor(state),
        background = SliderBackground.Gradient(colorTemperatureGradient(min, max)),
        service = ValueService(LIGHT, TURN_ON, entityData(state), "color_temp_kelvin"),
    )
}

/** Port of `generateColorTemperatureGradient`: eleven stops from [min] to [max] kelvin. */
fun colorTemperatureGradient(min: Double, max: Double): List<Pair<Double, String>> = (0..GRADIENT_STEPS).map { i ->
    1.0 / GRADIENT_STEPS * i to rgb2hex(temperature2rgb(min + (max - min) / GRADIENT_STEPS * i))
}

/** The power button, the main control choices and the white button, as upstream groups them. */
private fun HassSnapshot.lightButtons(
    state: EntityState,
    modes: Set<String>,
    supportsColor: Boolean,
    supportsColorTemp: Boolean,
): List<LightButton> {
    val enabled = state.available()
    val choices = buildList {
        if (supportsColor || supportsColorTemp) {
            add(LightButton.Separator)
            add(LightButton.Show(attributeName(state, "brightness"), enabled, LightMainControl.Brightness))
        }
        if (supportsColor) add(LightButton.Show(localize("$LIGHT_STRINGS.color"), enabled, LightMainControl.Color))
        if (supportsColorTemp) {
            add(LightButton.Show(localize("$LIGHT_STRINGS.color_temp"), enabled, LightMainControl.ColorTemp))
        }
    }
    val power = CardAction.CallService(
        LIGHT,
        if (state.state == ON) "turn_off" else TURN_ON,
        entityData(state),
        target = null,
    )
    val white = CardAction.CallService(
        LIGHT,
        TURN_ON,
        JsonObject(entityData(state) + ("white" to JsonPrimitive(true))),
        target = null,
    )
    return buildList {
        add(LightButton.Power(localize("$LIGHT_STRINGS.toggle"), enabled, power))
        addAll(choices)
        if (WHITE in modes) {
            add(LightButton.Separator)
            add(LightButton.White(localize("$LIGHT_STRINGS.set_white"), enabled, white))
        }
    }
}

/** The effect menu, for a light that has effects. */
private fun HassSnapshot.effectMenu(state: EntityState): SelectMenu? {
    val effects = state.attributes.array("effect_list")?.mapNotNull { it.stringOrNull }
    if (!state.supportsFeature(FEATURE_EFFECT) || effects == null) return null
    return SelectMenu(
        label = attributeName(state, "effect"),
        icon = "mdi:creation",
        value = state.attributes.string("effect"),
        enabled = state.available(),
        options = effects.map { effect ->
            MenuOption(
                value = effect,
                label = formatEntityAttributeValue(state, "effect", JsonPrimitive(effect)),
                icon = attributeIcon(state, "effect", effect),
                action = CardAction.CallService(
                    LIGHT,
                    TURN_ON,
                    JsonObject(entityData(state) + ("effect" to JsonPrimitive(effect))),
                    target = null,
                ),
            )
        },
    )
}

private const val LIGHT = "light"
private const val ON = "on"
private const val TURN_ON = "turn_on"
private const val COLOR_TEMP = "color_temp"
private const val WHITE = "white"
private const val LIGHT_STRINGS = "ui.dialogs.more_info_control.light"
private const val PERCENT = 100.0
private const val BRIGHTNESS_MAX = 255.0
private const val BACKGROUND_OPACITY = 0.2f
private const val GRADIENT_STEPS = 10
private const val FEATURE_EFFECT = 4
private val COLOR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")
private val BRIGHTNESS_MODES = COLOR_MODES + setOf(COLOR_TEMP, "brightness", WHITE)
