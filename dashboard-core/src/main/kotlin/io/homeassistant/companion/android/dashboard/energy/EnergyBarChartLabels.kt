package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// The labels of the energy bar charts: the axes' ticks, as ECharts picks them, and the tooltip of a period, as
// `getCommonOptions` formats it (frontend@20260624.6 src/panels/lovelace/cards/energy/common/energy-chart-options.ts).

/**
 * Round values for a value axis from [min] to [max], about [splitNumber] intervals apart: ECharts'
 * `intervalScaleNiceTicks`. The axis includes 0 unless [scale]d to the values (ECharts' `scale: true`), and one
 * value alone is in the middle of an axis as wide as it.
 */
fun niceTicks(min: Double, max: Double, splitNumber: Int = DEFAULT_SPLIT, scale: Boolean = false): List<Double> {
    var low = if (scale) min else minOf(min, 0.0)
    var high = if (scale) max else maxOf(max, 0.0)
    if (scale && low == high && low.isFinite()) {
        val expand = abs(low).takeIf { it != 0.0 } ?: 1.0
        low -= expand / 2
        high += expand / 2
    }
    val span = high - low
    if (span <= 0 || !span.isFinite()) return listOf(0.0, 1.0)
    val interval = niceNumber(span / splitNumber)
    val start = floor(low / interval) * interval
    val end = ceil(high / interval) * interval
    val count = ((end - start) / interval).toInt()
    return (0..count).map { roundToInterval(start + it * interval, interval) }
}

/** ECharts' `nice(value, round = true)`: 1, 2, 3, 5 or 10 times a power of ten. */
private fun niceNumber(value: Double): Double {
    val exponent = floor(log10(value))
    val power = TEN.pow(exponent)
    val fraction = value / power
    val nice = when {
        fraction < NICE_1 -> 1.0
        fraction < NICE_2 -> 2.0
        fraction < NICE_3 -> 3.0
        fraction < NICE_5 -> 5.0
        else -> TEN
    }
    return nice * power
}

/** Removes the floating point noise of multiples of [interval]. */
private fun roundToInterval(value: Double, interval: Double): Double {
    val digits = maxOf(0, -floor(log10(interval)).toInt() + 1)
    return BigDecimal.valueOf(value).setScale(digits, java.math.RoundingMode.HALF_UP).toDouble()
}

/**
 * The times the time axis of [chart] has labels at: hours, days or months, as many apart as fit about [maxLabels].
 */
fun DisplayFormats.timeTicks(chart: EnergyBarChart, maxLabels: Int): List<Long> =
    timeTicks(chart.xMin, chart.xMax, chart.period, maxLabels)

/**
 * The times a time axis from [xMin] to [xMax] with statistics by [period] has labels at: hours, days or months, as
 * many apart as fit about [maxLabels].
 */
fun DisplayFormats.timeTicks(xMin: Long, xMax: Long, period: StatisticPeriod, maxLabels: Int): List<Long> {
    val first = Instant.ofEpochMilli(xMin).atZone(zone)
    val last = Instant.ofEpochMilli(xMax)
    val unit = when (period) {
        StatisticPeriod.FIVE_MINUTES, StatisticPeriod.HOUR -> ChronoUnit.HOURS
        StatisticPeriod.DAY -> ChronoUnit.DAYS
        StatisticPeriod.MONTH -> ChronoUnit.MONTHS
    }
    val start = when (unit) {
        ChronoUnit.MONTHS -> first.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
        else -> first.truncatedTo(unit)
    }
    val all = generateSequence(start) { it.plus(1, unit) }
        .takeWhile { !it.toInstant().isAfter(last) }
        .filter { !it.toInstant().isBefore(first.toInstant()) }
        .toList()
    val steps = TICK_STEPS.getValue(unit)
    val step = steps.firstOrNull { all.size / it <= maxLabels } ?: steps.last()
    return all.filterIndexed { index, time ->
        if (unit ==
            ChronoUnit.HOURS
        ) {
            time.hour % step == 0
        } else {
            index % step == 0
        }
    }
        .map { it.toInstant().toEpochMilli() }
}

/** The label of a time axis tick: the hour, the day or the month. */
fun DisplayFormats.timeTickLabel(tick: Long, period: StatisticPeriod): String {
    val time = Instant.ofEpochMilli(tick).atZone(zone)
    return when (period) {
        StatisticPeriod.FIVE_MINUTES, StatisticPeriod.HOUR -> time(time.toInstant())
        StatisticPeriod.DAY -> datePart(time.toLocalDate(), DatePart.DAY_MONTH_SHORT)
        StatisticPeriod.MONTH -> datePart(time.toLocalDate(), DatePart.MONTH_SHORT)
    }
}

/** A value axis label with [digits] fraction digits, none for 0. Port of `createYAxisLabelFormatter`. */
fun DisplayFormats.valueLabel(value: Double, digits: Int): String =
    number(BigDecimal.valueOf(value), if (value == 0.0) 0 else digits, digits)

/**
 * What a period's tooltip shows: its time and each series' value, with the total of the positive bars when there
 * are several. Port of `formatTooltip`.
 */
data class EnergyTooltip(
    val title: String,
    val rows: List<EnergyTooltipRow>,
    val total: Double?,
    val lineRows: List<EnergyTooltipLineRow> = emptyList(),
)

/** A series' value in a tooltip. */
data class EnergyTooltipRow(val series: EnergyBarSeries, val value: String)

/** A line's value in a tooltip, after the bars'. */
data class EnergyTooltipLineRow(val line: EnergyLineSeries, val value: String)

/** The tooltip of the bars of [series] that start at [start], or `null` when they are all 0. */
fun DisplayFormats.energyTooltip(chart: EnergyBarChart, series: List<EnergyBarSeries>, start: Long): EnergyTooltip? {
    val bars = series.mapNotNull { s -> s.points.firstOrNull { it.start == start }?.let { s to it } }
    fun value(y: Double): String? {
        val text = number(BigDecimal.valueOf(y), 0, if (y < SMALL_VALUE) SMALL_DIGITS else DEFAULT_DIGITS)
        return if (text == "0") null else "$text ${chart.unit}"
    }
    val rows = bars.mapNotNull { (s, bar) -> value(bar.y)?.let { EnergyTooltipRow(s, it) } }
    // The lines' points at the bars' place
    val x = bars.firstOrNull()?.second?.x
    val lineRows = chart.lines.mapNotNull { line ->
        line.points.firstOrNull { it.x == x }?.let { point -> value(point.y)?.let { EnergyTooltipLineRow(line, it) } }
    }
    if (rows.isEmpty() && lineRows.isEmpty()) return null
    val positives = bars.filter { (s, bar) -> bar.y > 0 && rows.any { it.series == s } }.map { it.second.y }
    return EnergyTooltip(
        title = tooltipTitle(chart, start),
        rows = rows,
        total = positives.sum().takeIf { positives.size > 1 && it != 0.0 },
        lineRows = lineRows,
    )
}

private fun DisplayFormats.tooltipTitle(chart: EnergyBarChart, start: Long): String {
    val time = Instant.ofEpochMilli(start).atZone(zone)
    val date = time.toLocalDate()
    val showYear = chart.showCompareYear
    return when (chart.period) {
        StatisticPeriod.MONTH -> datePart(date, DatePart.MONTH_YEAR)
        StatisticPeriod.DAY -> datePart(
            date,
            if (showYear) DatePart.WEEKDAY_DAY_MONTH_YEAR else DatePart.WEEKDAY_DAY_MONTH,
        )
        else -> {
            val day = if (chart.compare) {
                datePart(date, if (showYear) DatePart.DAY_MONTH_YEAR_SHORT else DatePart.DAY_MONTH_SHORT) + ": "
            } else {
                ""
            }
            "$day${time(time.toInstant())} – ${time(time.plusHours(1).toInstant())}"
        }
    }
}

private const val DEFAULT_SPLIT = 5
private const val TEN = 10.0
private const val NICE_1 = 1.5
private const val NICE_2 = 2.5
private const val NICE_3 = 4.0
private const val NICE_5 = 7.0
private const val SMALL_VALUE = 0.1
private const val SMALL_DIGITS = 3
private const val DEFAULT_DIGITS = 2
private val TICK_STEPS = mapOf(
    ChronoUnit.HOURS to listOf(1, 2, 3, 4, 6, 12),
    ChronoUnit.DAYS to listOf(1, 2, 3, 5, 7, 14),
    ChronoUnit.MONTHS to listOf(1, 2, 3, 6),
)
