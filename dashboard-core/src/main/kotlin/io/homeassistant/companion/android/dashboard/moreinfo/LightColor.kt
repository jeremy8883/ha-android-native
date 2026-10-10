package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.color.hs2rgb
import io.homeassistant.companion.android.dashboard.color.rgbw2rgb
import io.homeassistant.companion.android.dashboard.color.rgbww2rgb
import io.homeassistant.companion.android.dashboard.color.temperature2rgb
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A colour a light can be set to (`LightColor`), as favourites store them. */
sealed interface LightColor {
    /** Hue in degrees and saturation 0–100. */
    data class Hs(val hue: Double, val saturation: Double) : LightColor

    /** A white, in kelvin. */
    data class ColorTemp(val kelvin: Double) : LightColor

    /** Red, green and blue. */
    data class Rgb(val channels: List<Double>) : LightColor

    /** Red, green, blue and white. */
    data class Rgbw(val channels: List<Double>) : LightColor

    /** Red, green, blue, cold and warm white. */
    data class Rgbww(val channels: List<Double>) : LightColor
}

/** The colour as a call's or a favourite's data: `{"hs_color": [h, s]}` and so on. */
fun LightColor.json(): JsonObject = JsonObject(
    mapOf(
        when (this) {
            is LightColor.Hs -> "hs_color" to numbers(listOf(hue, saturation))
            is LightColor.ColorTemp -> "color_temp_kelvin" to number(kelvin)
            is LightColor.Rgb -> "rgb_color" to numbers(channels)
            is LightColor.Rgbw -> "rgbw_color" to numbers(channels)
            is LightColor.Rgbww -> "rgbww_color" to numbers(channels)
        },
    ),
)

/** The colour a call's or a favourite's [data] sets, `null` when it sets none. */
fun parseLightColor(data: JsonObject): LightColor? {
    fun channels(key: String) = data.lightChannels(key)
    return data.lightChannels("hs_color")?.takeIf { it.size >= 2 }?.let { LightColor.Hs(it[0], it[1]) }
        ?: data.lightNumber("color_temp_kelvin")?.let { LightColor.ColorTemp(it) }
        ?: channels("rgb_color")?.let { LightColor.Rgb(it) }
        ?: channels("rgbw_color")?.let { LightColor.Rgbw(it) }
        ?: channels("rgbww_color")?.let { LightColor.Rgbww(it) }
}

/** Port of the favourite button's `_rgbColor`: the colour to show (whites at the default temperatures). */
internal fun LightColor.displayRgb(): DoubleArray = when (this) {
    is LightColor.Hs -> hs2rgb(hue, saturation / PERCENT)
    is LightColor.ColorTemp -> temperature2rgb(kelvin)
    is LightColor.Rgb -> channels.toDoubleArray()
    is LightColor.Rgbw -> rgbw2rgb(channels.toDoubleArray())
    is LightColor.Rgbww -> rgbww2rgb(channels.toDoubleArray(), null, null)
}

/** Port of `luminosity` (culori's `wcagLuminance`): the relative luminance of [rgb], 0–1. */
internal fun luminosity(rgb: DoubleArray): Double {
    fun linear(channel: Double): Double {
        val c = channel / RGB_MAX
        return if (abs(c) <=
            SRGB_LINEAR_LIMIT
        ) {
            c / SRGB_LINEAR_SCALE
        } else {
            ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
        }
    }
    return LUMA_RED * linear(rgb[0]) + LUMA_GREEN * linear(rgb[1]) + LUMA_BLUE * linear(rgb[2])
}

private fun number(value: Double): JsonPrimitive =
    if (value == Math.floor(value) && !value.isInfinite()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

private fun numbers(values: List<Double>) = JsonArray(values.map(::number))

private const val PERCENT = 100.0
private const val RGB_MAX = 255.0
private const val SRGB_LINEAR_LIMIT = 0.04045
private const val SRGB_LINEAR_SCALE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val LUMA_RED = 0.2126
private const val LUMA_GREEN = 0.7152
private const val LUMA_BLUE = 0.0722
