package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.color.hs2rgb
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The calls of `light-color-rgb-picker` (frontend@20260624.6 src/dialogs/more-info/components/lights/
// light-color-rgb-picker.ts) for a new colour, colour brightness or white.

/**
 * Port of `_updateColor`: the call that sets [state] to [hue] and [saturation], keeping its colour brightness
 * ([colorBrightness], 0–100, as the picker shows it).
 */
fun lightHueCall(
    state: EntityState,
    hue: Double,
    saturation: Double,
    colorBrightness: Double?,
): CardAction.CallService {
    val rgb = hs2rgb(hue, saturation)
    return when {
        supportsMode(state, RGBWW) || supportsMode(state, RGBW) -> {
            val adjusted = if (colorBrightness != null && colorBrightness != 0.0) {
                adjustColorBrightness(rgb, colorBrightness * RGB_MAX / PERCENT)
            } else {
                rgb
            }
            rgbWhiteCall(state, adjusted)
        }
        supportsMode(state, RGB) -> rgbCall(state, rgb)
        else -> turnOn(state, "hs_color" to jsonNumbers(doubleArrayOf(hue, saturation * PERCENT)))
    }
}

/** The RGB light's call: its colour scaled to the brightness its colour had, with the brightness to match. */
private fun rgbCall(state: EntityState, rgb: DoubleArray): CardAction.CallService {
    val adjusted = brightnessAdjusted(state) ?: return turnOn(state, "rgb_color" to jsonNumbers(rgb))
    val brightness = state.attributes.lightNumber("brightness") ?: 0.0
    val brightnessPct = Math.round(brightness * (adjusted / RGB_MAX * PERCENT) / RGB_MAX).toDouble()
    return turnOn(
        state,
        "rgb_color" to jsonNumbers(adjustColorBrightness(rgb, adjusted, invert = true)),
        "brightness_pct" to number(brightnessPct),
    )
}

/**
 * Port of `_colorBrightnessSliderChanged`: the call that sets the brightness of the colour channels from [from]
 * (as the picker showed it) to [to], 0–100.
 */
fun lightColorBrightnessCall(state: EntityState, from: Double?, to: Double): CardAction.CallService {
    val rgb = currentModeRgb(state)?.copyOf(RGB_SIZE) ?: doubleArrayOf(RGB_MAX, RGB_MAX, RGB_MAX)
    val normalized = if (from != null && from != 0.0) {
        adjustColorBrightness(rgb, from * RGB_MAX / PERCENT, invert = true)
    } else {
        rgb
    }
    return rgbWhiteCall(state, adjustColorBrightness(normalized, to * RGB_MAX / PERCENT))
}

/** Port of `_wvSliderChanged`: the call that sets [channel] to [value], 0–100. */
fun lightWhiteCall(state: EntityState, channel: LightChannel, value: Double): CardAction.CallService {
    val level = minOf(RGB_MAX, Math.round(value * RGB_MAX / PERCENT).toDouble())
    val current = currentModeRgb(state)
    if (channel == LightChannel.White) {
        val rgbw = (current ?: DoubleArray(RGBW_SIZE)).copyOf(maxOf(current?.size ?: 0, RGBW_SIZE))
        rgbw[WHITE_CHANNEL] = level
        return turnOn(state, "rgbw_color" to jsonNumbers(rgbw))
    }
    val rgbww = (current ?: DoubleArray(RGBWW_SIZE)).copyOf(maxOf(current?.size ?: 0, RGBWW_SIZE))
    rgbww[if (channel == LightChannel.ColdWhite) COLD_CHANNEL else WARM_CHANNEL] = level
    return turnOn(state, "rgbww_color" to jsonNumbers(rgbww))
}

/** Port of `_setRgbWColor`: [rgb] with the light's current white channels. */
private fun rgbWhiteCall(state: EntityState, rgb: DoubleArray): CardAction.CallService {
    val (attribute, size) = if (supportsMode(state, RGBWW)) "rgbww_color" to RGBWW_SIZE else "rgbw_color" to RGBW_SIZE
    val whites = state.attributes.lightChannels(attribute)?.drop(RGB_SIZE) ?: List(size - RGB_SIZE) { 0.0 }
    return turnOn(state, attribute to jsonNumbers(rgb.toList().plus(whites).toDoubleArray()))
}

/** Port of `_adjustColorBrightness`: [rgb] scaled from full brightness to [value] (0–255), or back with [invert]. */
private fun adjustColorBrightness(rgb: DoubleArray, value: Double?, invert: Boolean = false): DoubleArray {
    val color = if (rgb.all { it == 0.0 }) doubleArrayOf(RGB_MAX, RGB_MAX, RGB_MAX) else rgb.copyOf()
    if (value == null || value == RGB_MAX) return color
    val ratio = if (invert) RGB_MAX / value else value / RGB_MAX
    return DoubleArray(color.size) { minOf(RGB_MAX, Math.round(color[it] * ratio).toDouble()) }
}

/** How much an RGB light's colour is dimmed below full: its brightest channel, when under 255. */
private fun brightnessAdjusted(state: EntityState): Double? {
    val plainRgb = !supportsMode(state, RGBWW) && !supportsMode(state, RGBW)
    val inRgb = state.state == ON && state.attributes.string("color_mode") == RGB
    val rgb = state.attributes.lightChannels("rgb_color")?.takeIf { inRgb && plainRgb } ?: return null
    return rgb.max().takeIf { it < RGB_MAX }
}

private fun turnOn(state: EntityState, vararg data: Pair<String, JsonElement>) =
    CardAction.CallService("light", "turn_on", JsonObject(entityData(state) + data), target = null)

/** [value] as JSON, as JavaScript writes it: whole numbers without a fraction. */
private fun number(value: Double): JsonPrimitive =
    if (value == Math.floor(value) && !value.isInfinite()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

private fun jsonNumbers(values: DoubleArray) = JsonArray(values.map(::number))

private const val ON = "on"
private const val RGB = "rgb"
private const val RGBW = "rgbw"
private const val RGBWW = "rgbww"
private const val PERCENT = 100.0
private const val RGB_MAX = 255.0
private const val RGB_SIZE = 3
private const val RGBW_SIZE = 4
private const val RGBWW_SIZE = 5
private const val WHITE_CHANNEL = 3
private const val COLD_CHANNEL = 3
private const val WARM_CHANNEL = 4
