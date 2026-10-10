package io.homeassistant.companion.android.dashboard.color

import kotlin.math.ln
import kotlin.math.pow

// Ports of the frontend's light colour conversions (frontend@20260624.6 src/common/color/convert-light-color.ts).

/** Port of `temperature2rgb`: the colour of white light at [kelvin]. */
fun temperature2rgb(kelvin: Double): DoubleArray {
    val value = kelvin / KELVIN_SCALE
    return doubleArrayOf(
        Math.round(temperatureRed(value)).toDouble(),
        Math.round(temperatureGreen(value)).toDouble(),
        Math.round(temperatureBlue(value)).toDouble(),
    )
}

private fun temperatureRed(t: Double): Double =
    if (t <= WARM_LIMIT) RGB_MAX else (RED_FACTOR * (t - RED_OFFSET).pow(RED_EXPONENT)).coerceIn(0.0, RGB_MAX)

private fun temperatureGreen(t: Double): Double {
    val green = if (t <= WARM_LIMIT) {
        GREEN_LOG_FACTOR * ln(t) - GREEN_LOG_OFFSET
    } else {
        GREEN_FACTOR * (t - RED_OFFSET).pow(GREEN_EXPONENT)
    }
    return green.coerceIn(0.0, RGB_MAX)
}

private fun temperatureBlue(t: Double): Double = when {
    t >= WARM_LIMIT -> RGB_MAX
    t <= BLUE_LIMIT -> 0.0
    else -> (BLUE_FACTOR * ln(t - BLUE_OFFSET) - BLUE_LOG_OFFSET).coerceIn(0.0, RGB_MAX)
}

/** Port of `rgbw2rgb`: the colour an RGBW light shows, its white added to each channel. */
fun rgbw2rgb(rgbw: DoubleArray): DoubleArray {
    val (r, g, b) = Triple(rgbw[0], rgbw[1], rgbw[2])
    val w = rgbw[RGBW_WHITE]
    return matchMaxScale(rgbw, doubleArrayOf(r + w, g + w, b + w))
}

/**
 * Port of `rgbww2rgb`: the colour an RGBWW light shows, its whites added as the colour temperature between
 * [minKelvin] and [maxKelvin] they mix to.
 */
fun rgbww2rgb(rgbww: DoubleArray, minKelvin: Double?, maxKelvin: Double?): DoubleArray {
    val (r, g, b) = Triple(rgbww[0], rgbww[1], rgbww[2])
    val (cw, ww) = rgbww[RGBWW_COLD] to rgbww[RGBWW_WARM]
    val maxMireds = kelvin2mired(minKelvin ?: DEFAULT_MIN_KELVIN)
    val minMireds = kelvin2mired(maxKelvin ?: DEFAULT_MAX_KELVIN)
    // JavaScript divides 0 by 0 to NaN rather than throwing, so a light without white mixes to no temperature
    val ctRatio = ww / (cw + ww)
    val mired = minMireds + ctRatio * (maxMireds - minMireds)
    val kelvin = if (mired.isNaN() || mired == 0.0) 0.0 else mired2kelvin(mired)
    val white = temperature2rgb(kelvin)
    val level = maxOf(cw, ww) / RGB_MAX
    return matchMaxScale(rgbww, doubleArrayOf(r + white[0] * level, g + white[1] * level, b + white[2] * level))
}

/** Port of `kelvin2mired`. */
fun kelvin2mired(kelvin: Double): Double = if (kelvin == 0.0) MIREDS_SCALE else Math.floor(MIREDS_SCALE / kelvin)

/** Port of `mired2kelvin`. */
fun mired2kelvin(mired: Double): Double = if (mired == 0.0) MIREDS_SCALE else Math.floor(MIREDS_SCALE / mired)

/** Port of `matchMaxScale`: [output] scaled so that its largest channel is [input]'s, rounded. */
private fun matchMaxScale(input: DoubleArray, output: DoubleArray): DoubleArray {
    val maxIn = input.max()
    val maxOut = output.max()
    val factor = if (maxOut == 0.0) 0.0 else maxIn / maxOut
    return DoubleArray(output.size) { Math.round(output[it] * factor).toDouble() }
}

/** The warmest colour temperature a light reports when it gives none (`DEFAULT_MIN_KELVIN`). */
const val DEFAULT_MIN_KELVIN = 2700.0

/** The coolest colour temperature a light reports when it gives none (`DEFAULT_MAX_KELVIN`). */
const val DEFAULT_MAX_KELVIN = 6500.0

private const val RGBW_WHITE = 3
private const val RGBWW_COLD = 3
private const val RGBWW_WARM = 4
private const val MIREDS_SCALE = 1_000_000.0

// Tanner Helland's approximation, as upstream writes it
private const val KELVIN_SCALE = 100.0
private const val WARM_LIMIT = 66.0
private const val BLUE_LIMIT = 19.0
private const val RED_FACTOR = 329.698727446
private const val RED_OFFSET = 60.0
private const val RED_EXPONENT = -0.1332047592
private const val GREEN_LOG_FACTOR = 99.4708025861
private const val GREEN_LOG_OFFSET = 161.1195681661
private const val GREEN_FACTOR = 288.1221695283
private const val GREEN_EXPONENT = -0.0755148492
private const val BLUE_FACTOR = 138.5177312231
private const val BLUE_OFFSET = 10.0
private const val BLUE_LOG_OFFSET = 305.0447927307
