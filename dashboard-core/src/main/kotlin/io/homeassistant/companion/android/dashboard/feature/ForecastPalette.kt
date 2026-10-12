package io.homeassistant.companion.android.dashboard.feature

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

// The forecast features' label times and temperature colours: ports of common/graph-labels.ts and
// common/temperature-palette.ts (frontend@20260624.6 src/panels/lovelace/card-features/).

/** The entries whose day labels show: one per day (`renderDayLabels`). */
fun ForecastFeature.dayLabelTimes(entries: List<JsonObject>): List<Double> =
    entries.indices.step(entriesPerDay).mapNotNull { entries[it].timeMillis() }

/** Port of `renderHourLabels`: about four hours from now on, up to the last whole hour shown. */
fun ForecastFeature.hourLabelTimes(): List<Double> {
    val now = nowMillis ?: return emptyList()
    val maxTime = floor((now + hoursToShow * MS_PER_HOUR) / MS_PER_HOUR) * MS_PER_HOUR
    val step = maxOf(1, (hoursToShow / LABEL_COUNT.toDouble()).roundToInt())
    return (0..hoursToShow step step).map { now + it * MS_PER_HOUR }.filter { it <= maxTime }
}

/** Port of `renderHourSlotLabels`: every few hourly slots from the next whole hour, with their slot index. */
fun ForecastFeature.hourSlotLabelTimes(): List<Pair<Int, Double>> {
    val now = nowMillis ?: return emptyList()
    val start = ceil(now / MS_PER_HOUR) * MS_PER_HOUR
    val step = maxOf(1, (hoursToShow / LABEL_COUNT.toDouble()).roundToInt())
    return (0 until hoursToShow step step).map { it to start + it * MS_PER_HOUR }
}

/** A temperature in °C, as the palette is. */
internal fun ForecastFeature.celsius(temperature: Double): Double =
    if (fahrenheit) (temperature - FAHRENHEIT_OFFSET) * FAHRENHEIT_RATIO else temperature

/** Port of `getAbsoluteGradient`: the palette from [max] at the top to [min] at the bottom. */
internal fun absoluteGradient(min: Double, max: Double, stops: Int): List<GradientStop> =
    buildStops(min, max, stops) { it }

/** Port of `getRelativeGradient`: like [absoluteGradient], compressed towards the middle for cool ranges. */
internal fun relativeGradient(min: Double, max: Double, stops: Int): List<GradientStop> {
    val mid = (min + max) / 2
    val compression = (COMPRESSION_BASE + COMPRESSION_SLOPE * mid).coerceIn(MIN_COMPRESSION, 1.0)
    return buildStops(min, max, stops) { mid + compression * (it - mid) }
}

private fun buildStops(min: Double, max: Double, count: Int, effective: (Double) -> Double): List<GradientStop> =
    (0 until count).map { i ->
        val offset = i.toDouble() / (count - 1)
        GradientStop(offset, paletteAt(effective(max + offset * (min - max))))
    }

/** The palette's colour at [celsius], blended between its whole degrees. */
private fun paletteAt(celsius: Double): String {
    val index = celsius.coerceIn(PALETTE_MIN.toDouble(), (PALETTE_MIN + PALETTE.size - 1).toDouble()) - PALETTE_MIN
    val i0 = floor(index).toInt()
    val i1 = minOf(i0 + 1, PALETTE.size - 1)
    val fraction = index - i0
    val a = rgb(PALETTE[i0])
    val b = rgb(PALETTE[i1])
    return "#" + (0..2).joinToString("") { c ->
        Math.round(a[c] + (b[c] - a[c]) * fraction).toInt().coerceIn(0, RGB_MAX).toString(HEX).padStart(2, '0')
    }
}

private fun rgb(hex: String) = hex.removePrefix("#").chunked(2).map { it.toInt(HEX).toDouble() }

private const val LABEL_COUNT = 4
private const val FAHRENHEIT_OFFSET = 32.0
private const val FAHRENHEIT_RATIO = 5.0 / 9.0
private const val COMPRESSION_BASE = -0.15
private const val COMPRESSION_SLOPE = 0.025
private const val MIN_COMPRESSION = 0.05
private const val RGB_MAX = 255
private const val HEX = 16
private const val MS_PER_HOUR = 3_600_000.0

/** The palette's lowest degree. */
private const val PALETTE_MIN = -6

/** `PALETTE_HEX`: a colour per degree from -6 to 42 °C. */
private val PALETTE = listOf(
    "#249df2", "#239dec", "#239fec", "#23a3eb", "#23a6eb", "#23a9e9", "#22abe6", "#22aee4", "#22b1e0", "#21b1dd",
    "#23b6da", "#28b8d6", "#2abdd3", "#2fbfcf", "#34c4cc", "#36c6c9", "#43c9c0", "#59c9b3", "#6fc9a3", "#82c992",
    "#98c985", "#a8c977", "#b9c762", "#c7c74d", "#d1be31", "#dbb921", "#e6ba22", "#ecc123", "#ecba23", "#eeb424",
    "#ecaa23", "#eca023", "#ec9723", "#ec8f23", "#ec8523", "#ec7c23", "#ee7f24", "#e67122", "#df6321", "#df5b21",
    "#dd5421", "#db4c21", "#db4121", "#db3721", "#d62f20", "#cc301f", "#c22a1d", "#ca2d1e", "#c82c1d",
)
