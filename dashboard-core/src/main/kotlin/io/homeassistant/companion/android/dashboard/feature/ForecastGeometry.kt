package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.history.GraphBox
import io.homeassistant.companion.android.dashboard.history.GraphCoordinates
import io.homeassistant.companion.android.dashboard.history.GraphPoint
import io.homeassistant.companion.android.dashboard.history.Limits
import io.homeassistant.companion.android.dashboard.history.Sampling
import io.homeassistant.companion.android.dashboard.history.pointCoordinates
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.serialization.json.JsonObject

// Where the forecast features draw, in a box of the feature's size: ports of `_renderBars` and `_computeHourly`
// (hui-temperature-forecast-card-feature.ts), `_renderDailyBars` and `_renderHourlyBars`
// (hui-precipitation-forecast-card-feature.ts), the temperature palette (common/temperature-palette.ts) and the
// label times (common/graph-labels.ts).

/** A gradient stop: its offset from the top (0 to 1) and `#rrggbb` colour. */
data class GradientStop(val offset: Double, val color: String)

/** A rounded bar from [y] down [height]; its [stops] run top to bottom (`null` for a single colour). */
data class ForecastBar(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val radius: Double,
    val stops: List<GradientStop>?,
)

/** A precipitation mark: a bar rounded at the top, or a dot where there is none. */
sealed interface PrecipitationMark {
    /** A bar from [y] down [height], its top corners rounded by [radius]. */
    data class Bar(val x: Double, val y: Double, val width: Double, val height: Double, val radius: Double) :
        PrecipitationMark

    /** A dot of [radius] at ([cx], [cy]). */
    data class Dot(val cx: Double, val cy: Double, val radius: Double) : PrecipitationMark
}

/** The hourly temperature line and its gradient (top to bottom over the box), from `_computeHourly`. */
data class TemperatureLine(val coordinates: GraphCoordinates, val stops: List<GradientStop>)

/** The daily entries a feature draws: those with both temperatures (for temperatures), for the days to show. */
fun ForecastFeature.dailyEntries(): List<JsonObject> {
    val all = entries.orEmpty()
    val usable = if (kind is ForecastKind.Temperature) {
        all.filter { number(it["temperature"])?.isFinite() == true && number(it["templow"])?.isFinite() == true }
    } else {
        all
    }
    return usable.take(daysToShow * entriesPerDay)
}

/** Port of `_renderBars`: a bar per entry from its low to its high temperature, coloured by them. */
fun ForecastFeature.temperatureBars(entries: List<JsonObject>, width: Double, height: Double): List<ForecastBar> {
    if (entries.isEmpty()) return emptyList()
    val slot = width / entries.size
    val barWidth = maxOf(1.0, minOf(MAX_TEMPERATURE_BAR, slot - MIN_GAP))
    val drawable = height - BAR_PADDING * 2
    val highs = entries.map { number(it["temperature"]) ?: 0.0 }
    val lows = entries.map { number(it["templow"]) ?: 0.0 }
    var min = lows.min()
    var max = highs.max()
    if (min == max) {
        min -= 1
        max += 1
    }
    fun yFor(value: Double) = BAR_PADDING + drawable - (value - min) / (max - min) * drawable
    return entries.indices.map { i ->
        val yHigh = yFor(highs[i])
        val barHeight = maxOf(1.0, yFor(lows[i]) - yHigh)
        ForecastBar(
            x = slot * i + (slot - barWidth) / 2,
            y = yHigh,
            width = barWidth,
            height = barHeight,
            radius = minOf(barWidth / 2, barHeight / 2),
            stops = if (color == null) relativeGradient(celsius(lows[i]), celsius(highs[i]), BAR_STOPS) else null,
        )
    }
}

/**
 * Port of `_computeHourly`: the current temperature then the forecast up to [ForecastFeature.hoursToShow] hours, as
 * a line from now; `null` without a point or a time.
 */
fun ForecastFeature.temperatureLine(width: Double, height: Double): TemperatureLine? {
    val now = nowMillis ?: return null
    val maxTime = floor((now + hoursToShow * MS_PER_HOUR) / MS_PER_HOUR) * MS_PER_HOUR
    val forecast = entries.orEmpty().mapNotNull { entry ->
        val temperature = number(entry["temperature"])?.takeIf { !it.isNaN() }
        temperature?.let { entry.timeMillis()?.let { time -> GraphPoint(time, it) } }
    }
    // Upstream stops at the first entry past the window
    val data = listOfNotNull(currentTemperature?.takeIf { !it.isNaN() }?.let { GraphPoint(now, it) }) +
        forecast.takeWhile { it.x <= maxTime }.filter { it.x >= now }
    return data.takeIf { it.isNotEmpty() }?.let { temperatureLine(it, now, maxTime, GraphBox(width, height)) }
}

private fun ForecastFeature.temperatureLine(
    data: List<GraphPoint>,
    now: Double,
    maxTime: Double,
    box: GraphBox,
): TemperatureLine {
    val width = box.width
    val height = box.height
    val dataMin = data.minOf { it.y }
    val dataMax = data.maxOf { it.y }
    val range = (dataMax - dataMin).takeUnless { it == 0.0 } ?: (dataMin * MARGIN)
    val minY = dataMin - range * MARGIN
    val maxY = dataMax + range * MARGIN
    val coordinates = pointCoordinates(
        data,
        GraphBox(width, height),
        Sampling(data.size.toDouble(), useMean = false),
        Limits(now, maxTime, minY, maxY),
    )
    // `coordinates` ends with a flat step to the right edge, which upstream drops
    val points = coordinates.points.dropLast(1)
    // The curve lives in the range calcPoints widens by another tenth
    val padRange = (maxY - minY).takeUnless { it == 0.0 } ?: (minY * MARGIN)
    val stops = absoluteGradient(celsius(minY - padRange * MARGIN), celsius(maxY + padRange * MARGIN), LINE_STOPS)
    return TemperatureLine(coordinates.copy(points = points), stops)
}

/** Ports of `_renderDailyBars` and `_renderHourlyBars`: a bar per entry, scaled to the wettest, or a dot when dry. */
fun ForecastFeature.precipitationMarks(width: Double, height: Double): List<PrecipitationMark> =
    if (hourly) hourlyPrecipitation(width, height) else dailyPrecipitation(dailyEntries(), width, height)

private fun ForecastFeature.dailyPrecipitation(
    entries: List<JsonObject>,
    width: Double,
    height: Double,
): List<PrecipitationMark> {
    if (entries.isEmpty()) return emptyList()
    val slot = width / entries.size
    val barWidth = maxOf(1.0, minOf(MAX_PRECIPITATION_BAR, slot - MIN_GAP))
    val drawable = height - BAR_PADDING * 2
    val values = entries.map(::precipitation)
    val max = maxPrecipitation(values)
    return values.mapIndexed { i, value ->
        val x = slot * i + slot / 2
        if (value == null || isDry(value, max)) {
            PrecipitationMark.Dot(x, BAR_PADDING + drawable - DOT_RADIUS, DOT_RADIUS)
        } else {
            val barHeight = maxOf(1.0, value / max * drawable)
            PrecipitationMark.Bar(
                x - barWidth / 2,
                BAR_PADDING + drawable - barHeight,
                barWidth,
                barHeight,
                barWidth / CORNER_DIVISOR,
            )
        }
    }
}

private fun ForecastFeature.hourlyPrecipitation(width: Double, height: Double): List<PrecipitationMark> {
    val now = nowMillis ?: return emptyList()
    val start = ceil(now / MS_PER_HOUR) * MS_PER_HOUR
    val range = hoursToShow * MS_PER_HOUR
    val inRange = entries.orEmpty().mapNotNull { entry ->
        entry.timeMillis()?.takeIf { it >= start && it < start + range }?.let { it to precipitation(entry) }
    }
    val max = maxPrecipitation(inRange.map { it.second })
    val slot = width / hoursToShow
    val barWidth = maxOf(1.0, minOf(MAX_PRECIPITATION_BAR, slot - HOURLY_GAP))
    val drawable = height - BAR_PADDING
    return inRange.map { (time, value) ->
        // Each entry is an hour's slot; the bar is centred in it
        val center = (time - start) / range * width + slot / 2
        if (value == null || isDry(value, max)) {
            PrecipitationMark.Dot(center, height - DOT_RADIUS, DOT_RADIUS)
        } else {
            val barHeight = maxOf(1.0, value / max * drawable)
            PrecipitationMark.Bar(
                center - barWidth / 2,
                height - barHeight,
                barWidth,
                barHeight,
                barWidth / CORNER_DIVISOR,
            )
        }
    }
}

/** Whether [value] draws as a dot: none, or nothing to scale it against. */
private fun isDry(value: Double, max: Double): Boolean = !value.isFinite() || value <= 0 || max <= 0

/** Port of `getForecastPrecipitation`. */
private fun ForecastFeature.precipitation(entry: JsonObject): Double? = number(
    entry[
        if ((kind as? ForecastKind.Precipitation)?.probability ==
            true
        ) {
            "precipitation_probability"
        } else {
            "precipitation"
        },
    ],
)

/** The value a full bar stands for: 100% for probabilities, else `_referenceMaxAmount` or the wettest. */
private fun ForecastFeature.maxPrecipitation(values: List<Double?>): Double {
    if ((kind as? ForecastKind.Precipitation)?.probability == true) return PERCENT
    val reference = when {
        hourly -> if (imperial) 0.1 else 2.5
        entriesPerDay == 2 -> if (imperial) 0.25 else 6.0
        else -> if (imperial) 0.4 else 10.0
    }
    return values.filterNotNull().filter { it.isFinite() }.fold(reference, ::maxOf)
}

private const val MS_PER_HOUR = 3_600_000.0
private const val MAX_TEMPERATURE_BAR = 8.0
private const val MAX_PRECIPITATION_BAR = 16.0
private const val MIN_GAP = 4.0
private const val BAR_PADDING = 4.0
private const val DOT_RADIUS = 1.5
private const val BAR_STOPS = 3
private const val CORNER_DIVISOR = 4
private const val HOURLY_GAP = 2
private const val LINE_STOPS = 5
private const val MARGIN = 0.1
