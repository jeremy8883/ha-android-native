package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.derive.jsParseFloat
import io.homeassistant.companion.android.dashboard.energy.StatisticValue
import io.homeassistant.companion.android.dashboard.energy.yAxisFractionDigits
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot

// The statistics chart of the more-info dialog: a sensor's 5-minute mean, with the band between its minimum and
// maximum. Port of `generateStatisticsChartData` for its line charts without legend (frontend@20260624.6
// src/components/chart/statistics-chart-data.ts).

/** A statistic's lines, in its [unit]. */
data class StatisticsChart(val series: List<StatisticSeries>, val unit: String?, val yFractionDigits: Int)

/**
 * A line of a statistic's [type] (`min`, `mean`, `max`...). A [band] line is an edge of the band between the
 * minimum and maximum, drawn without its line ([hidden]); the band's top is stacked on its bottom, with the area
 * between them.
 */
data class StatisticSeries(
    val id: String,
    val type: String,
    val name: String,
    val band: Boolean,
    val hidden: Boolean,
    val bandTop: Boolean,
    val points: List<StatisticPoint>,
)

/**
 * The statistic at [x] (epoch ms): its [value], `null` for a gap. A band's top also has its [height] above the
 * bottom, which ECharts stacks on it.
 */
data class StatisticPoint(val x: Double, val value: Double?, val height: Double? = null)

/**
 * The chart of [stats] of [statisticId] in [unit] at [now] (epoch ms): the minimum, mean and maximum (and state)
 * the statistics have, with the current state added at [now] when they reach to within 10 minutes of it.
 */
fun HassSnapshot.statisticsChart(
    statisticId: String,
    stats: List<StatisticValue>,
    unit: String?,
    now: Double,
): StatisticsChart {
    val present = SORTED_TYPES.filter { type -> stats.any { it.value(type) != null } }
    val band = BandTypes(present)
    val end = minOf(stats.lastOrNull()?.start?.toDouble() ?: now, now)
    val drafts = present.map(band::draft)
    val chart = StatisticsBuilder(drafts, end)
    var previousStart: Long? = null
    stats.forEach { stat ->
        if (previousStart == stat.start) return@forEach
        previousStart = stat.start
        chart.push(stat.start.toDouble(), stat.end.toDouble(), drafts.map { it.pointAt(stat, band.bottom) })
    }
    chart.close()
    // The current state (not of external statistics) when the statistics reach close to now
    val current = states[statisticId]?.state?.let(::jsParseFloat)?.takeIf { it.isFinite() }
    if (now - end <= CURRENT_STATE_LEEWAY && !statisticId.contains(':') && current != null) {
        drafts.forEach { draft ->
            draft.points += if (draft.top) StatisticPoint(now, current, 0.0) else StatisticPoint(now, current)
            chart.track(current)
        }
    }
    return StatisticsChart(
        series = drafts.map { draft ->
            StatisticSeries(
                id = "$statisticId-${draft.type}",
                type = draft.type,
                name = localize("ui.components.statistics_charts.statistic_types.${draft.type}"),
                band = draft.band,
                hidden = draft.hidden,
                bandTop = draft.top,
                points = draft.points.toList(),
            )
        },
        unit = unit,
        yFractionDigits = yAxisFractionDigits(chart.yMin, chart.yMax),
    )
}

/** Which of the statistic's [types] draw the band between the minimum and maximum, and which its line. */
private class BandTypes(types: List<String>) {
    private val hasMean = MEAN in types
    private val hasMin = MIN in types
    private val hasMax = MAX in types
    private val drawBands = listOf(hasMean, hasMax, hasMin).count { it } > 1
    private val top = if (hasMax) MAX else MEAN
    val bottom = if (hasMin) MIN else MEAN

    fun draft(type: String): StatisticDraft {
        val band = drawBands && (type == top || type == bottom)
        return StatisticDraft(
            type = type,
            band = band,
            // The band's edges vanish when it's between a minimum and a maximum around the mean
            hidden = band && hasMin && hasMax && hasMean,
            top = drawBands && type == top,
        )
    }
}

private class StatisticDraft(val type: String, val band: Boolean, val hidden: Boolean, val top: Boolean) {
    val points = mutableListOf<StatisticPoint>()

    /** The point of [stat]: its value, and for the band's top its height above [bottom]. */
    fun pointAt(stat: StatisticValue, bottom: String): StatisticPoint = if (top) {
        val topValue = stat.value(type) ?: 0.0
        StatisticPoint(0.0, topValue, kotlin.math.abs(topValue - (stat.value(bottom) ?: 0.0)))
    } else {
        StatisticPoint(0.0, stat.value(type))
    }
}

/** Port of the line chart's `pushData`: points at each start, a gap where periods don't follow on. */
private class StatisticsBuilder(private val drafts: List<StatisticDraft>, private val end: Double) {
    var yMin = Double.POSITIVE_INFINITY
    var yMax = Double.NEGATIVE_INFINITY
    private var previous: List<StatisticPoint>? = null
    private var previousEnd: Double? = null

    fun track(value: Double?) {
        if (value != null && value.isFinite()) {
            yMin = minOf(yMin, value)
            yMax = maxOf(yMax, value)
        }
    }

    fun push(start: Double, periodEnd: Double, values: List<StatisticPoint>) {
        val limit = minOf(periodEnd, end)
        if (start > limit) return
        val prev = previous
        val prevEnd = previousEnd
        drafts.forEachIndexed { i, draft ->
            // A gap where the previous period didn't end at this one's start
            if (prev != null && prevEnd != null && prevEnd != start) {
                draft.points += prev[i].copy(x = prevEnd)
                draft.points += StatisticPoint(prevEnd, null)
            }
            draft.points += values[i].copy(x = start)
            track(values[i].value)
        }
        previous = values
        previousEnd = limit
    }

    /** Closes the last period at its end. */
    fun close() {
        val prev = previous ?: return
        val prevEnd = previousEnd ?: return
        drafts.forEachIndexed { i, draft -> draft.points += prev[i].copy(x = prevEnd) }
    }
}

private fun StatisticValue.value(type: String): Double? = when (type) {
    MIN -> min
    MEAN -> mean
    MAX -> max
    STATE -> state
    else -> null
}

private const val MIN = "min"
private const val MEAN = "mean"
private const val MAX = "max"
private const val STATE = "state"

// The more-info dialog's types, the minimum first and the maximum last for the band
private val SORTED_TYPES = listOf(MIN, STATE, MEAN, MAX)
private const val CURRENT_STATE_LEEWAY = 600_000.0
