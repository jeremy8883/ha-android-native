package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.color.rgb2hsv
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// Port of `light-color-rgb-picker` (frontend@20260624.6 src/dialogs/more-info/components/lights/
// light-color-rgb-picker.ts): the hue and saturation wheel, and the brightness and white sliders of RGBW and RGBWW
// lights, with the calls each makes.

/**
 * What a light's colour picker shows.
 *
 * @property hue the hue of the light's colour in degrees, `null` while it is off or has none
 * @property saturation its saturation, 0–1
 * @property colorBrightness the brightness of its colour channels, 0–100
 * @property white an RGBW light's white channel, 0–100
 * @property coldWhite an RGBWW light's cold white channel, 0–100
 * @property warmWhite an RGBWW light's warm white channel, 0–100
 * @property sliders the sliders under the wheel
 */
data class LightColorPicker(
    val hue: Double?,
    val saturation: Double?,
    val colorBrightness: Double?,
    val white: Double?,
    val coldWhite: Double?,
    val warmWhite: Double?,
    val minKelvin: Double?,
    val maxKelvin: Double?,
    val sliders: List<LightChannelSlider>,
)

/** A slider under the wheel (`ha-labeled-slider`), 0–100. */
data class LightChannelSlider(val channel: LightChannel, val caption: String, val icon: String, val value: Double?)

/** What a [LightChannelSlider] sets. */
sealed interface LightChannel {
    /** The brightness of the colour channels. */
    data object ColorBrightness : LightChannel

    /** An RGBW light's white. */
    data object White : LightChannel

    /** An RGBWW light's cold white. */
    data object ColdWhite : LightChannel

    /** An RGBWW light's warm white. */
    data object WarmWhite : LightChannel
}

/** The colour picker of [state], a light that takes colours. Port of `_updateSliderValues`. */
fun HassSnapshot.lightColorPicker(state: EntityState): LightColorPicker {
    val on = state.state == ON
    val mode = state.attributes.string("color_mode")
    val rgbww = supportsMode(state, RGBWW)
    val rgbw = !rgbww && supportsMode(state, RGBW)
    val current = currentModeRgb(state)?.takeIf { on }
    val hsv = current?.let { rgb2hsv(it[0], it[1], it[2]) }
    val colorBrightness = current?.let { Math.round(maxOf(it[0], it[1], it[2]) * PERCENT / RGB_MAX).toDouble() }
    fun channel(attribute: String, colorMode: String, index: Int) = state.attributes.lightChannels(attribute)
        ?.takeIf { on && mode == colorMode }?.getOrNull(index)?.let { Math.round(it * PERCENT / RGB_MAX).toDouble() }
    val white = channel("rgbw_color", RGBW, WHITE_CHANNEL)
    val cold = channel("rgbww_color", RGBWW, COLD_CHANNEL)
    val warm = channel("rgbww_color", RGBWW, WARM_CHANNEL)
    val sliders = buildList {
        if (rgbw || rgbww) {
            add(slider(LightChannel.ColorBrightness, "color_brightness", "mdi:brightness-7", colorBrightness))
        }
        if (rgbw) add(slider(LightChannel.White, "white_value", "mdi:file-word-box", white))
        if (rgbww) {
            add(slider(LightChannel.ColdWhite, "cold_white_value", "mdi:file-word-box-outline", cold))
            add(slider(LightChannel.WarmWhite, "warm_white_value", "mdi:file-word-box", warm))
        }
    }
    return LightColorPicker(
        hue = hsv?.get(0),
        saturation = hsv?.get(1),
        colorBrightness = colorBrightness,
        white = white,
        coldWhite = cold,
        warmWhite = warm,
        minKelvin = state.attributes.lightNumber("min_color_temp_kelvin"),
        maxKelvin = state.attributes.lightNumber("max_color_temp_kelvin"),
        sliders = sliders,
    )
}

private fun HassSnapshot.slider(channel: LightChannel, key: String, icon: String, value: Double?) =
    LightChannelSlider(channel, localize("ui.card.light.$key"), icon, value)

/** Port of `getLightCurrentModeRgbColor`: the channels of the light's colour mode. */
internal fun currentModeRgb(state: EntityState): DoubleArray? = when (state.attributes.string("color_mode")) {
    RGBWW -> state.attributes.lightChannels("rgbww_color")
    RGBW -> state.attributes.lightChannels("rgbw_color")
    else -> state.attributes.lightChannels("rgb_color")
}?.toDoubleArray()

internal fun supportsMode(state: EntityState, mode: String) =
    state.attributes.array("supported_color_modes")?.any { it.stringOrNull == mode } == true

internal fun JsonObject.lightChannels(key: String): List<Double>? = array(key)?.map(::jsNumber)

internal fun JsonObject.lightNumber(key: String): Double? = this[key]?.takeIf { it !is JsonNull }?.let(::jsNumber)

private const val ON = "on"
private const val RGBW = "rgbw"
private const val RGBWW = "rgbww"
private const val PERCENT = 100.0
private const val RGB_MAX = 255.0
private const val WHITE_CHANNEL = 3
private const val COLD_CHANNEL = 3
private const val WARM_CHANNEL = 4
