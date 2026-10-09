package io.homeassistant.companion.android.dashboard.energy

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max

// The bar charts of the energy cards, as their ECharts options describe them. Port of the helpers of
// frontend@20260624.6 src/panels/lovelace/cards/energy/common/energy-chart-options.ts.

/**
 * A bar chart of energy by period: [series] stacked in order, the compared period's beside the shown one's, and
 * [lines] over them (the solar forecast).
 *
 * @property xMin the time axis's start, epoch ms
 * @property xMax the time axis's end, epoch ms
 * @property yFractionDigits the fraction digits of the y axis's labels
 * @property showCompareYear whether the compared period is in another year, which tooltips then show
 */
data class EnergyBarChart(
    val series: List<EnergyBarSeries>,
    val xMin: Long,
    val xMax: Long,
    val period: StatisticPeriod,
    val yFractionDigits: Int,
    val unit: String,
    val compare: Boolean,
    val showCompareYear: Boolean = false,
    val lines: List<EnergyLineSeries> = emptyList(),
) {
    /** Whether no series has any bar or point, which upstream shows as "no data". */
    val isEmpty: Boolean get() = series.all { it.points.isEmpty() } && lines.all { it.points.isEmpty() }
}

/** A dashed line over the bars, in the theme's [color] variable. */
data class EnergyLineSeries(val id: String, val name: String, val color: String, val points: List<EnergyLinePoint>)

/** A point of an [EnergyLineSeries]: [y] at [x], epoch ms. */
data class EnergyLinePoint(val x: Long, val y: Double)

/**
 * One series of bars.
 *
 * @property kind what the series measures (`from_grid`, `used_solar`...), which decides its colour
 * @property colorIndex the series' position among those of its kind, which darkens (or lightens) the colour
 */
data class EnergyBarSeries(
    val id: String,
    val name: String,
    val kind: String,
    val colorIndex: Int?,
    val compare: Boolean,
    val order: Int,
    val points: List<EnergyBarPoint>,
)

/** A bar at [x] (centred in its period for sub-daily periods) of [y], for the period starting at [start]. */
data class EnergyBarPoint(val x: Long, val y: Double, val start: Long)

/** The prefix of the compared period's series ids. */
const val COMPARE_PREFIX = "compare-"

/**
 * How far a sub-daily bar is moved to sit in the middle of its period: half the gap between the first two
 * periods, or half the period's length. Port of the `periodOffset` of the energy graph cards.
 */
fun periodMidpointOffset(period: StatisticPeriod, starts: List<Long>): Long {
    val subDaily = period == StatisticPeriod.HOUR || period == StatisticPeriod.FIVE_MINUTES
    return if (subDaily && starts.size >= 2) (starts[1] - starts[0]) / 2 else periodMidpointOffset(period)
}

/** Half a sub-daily period's length, 0 otherwise. Port of `getPeriodMidpointOffset`. */
fun periodMidpointOffset(period: StatisticPeriod): Long = when (period) {
    StatisticPeriod.FIVE_MINUTES -> FIVE_MINUTES_MS / 2
    StatisticPeriod.HOUR -> HOUR_MS / 2
    else -> 0
}

/**
 * Where the compared period's times are drawn: moved onto the shown period by whole years, months or days when
 * the period starts at one, else by its offset. Port of `getCompareTransform`.
 */
fun compareTransform(data: EnergyData, zone: ZoneId): (Long) -> Long {
    val comparePeriod = data.comparePeriod ?: return { it }
    val start = data.period.start.atStartOfDay(zone)
    val compareStart = comparePeriod.start.atStartOfDay(zone)
    val days = ChronoUnit.DAYS.between(compareStart, start)
    val years = ChronoUnit.YEARS.between(compareStart, start)
    val months = ChronoUnit.MONTHS.between(compareStart, start)
    fun shift(by: (ZonedDateTime) -> ZonedDateTime): (Long) -> Long = { ts ->
        val time = Instant.ofEpochMilli(ts).atZone(zone)
        val shifted = by(time)
        // Shifting clamps Jan 31 to Feb 28; shift by days then, so each day keeps its own place
        (if (shifted.dayOfMonth == time.dayOfMonth) shifted else time.plusDays(days)).toInstant().toEpochMilli()
    }
    val offset = start.toInstant().toEpochMilli() - compareStart.toInstant().toEpochMilli()
    return when {
        years != 0L && start.dayOfYear == 1 -> shift { it.plusYears(years) }
        months != 0L && start.dayOfMonth == 1 -> shift { it.plusMonths(months) }
        days != 0L -> { ts -> Instant.ofEpochMilli(ts).atZone(zone).plusDays(days).toInstant().toEpochMilli() }
        else -> { ts -> ts + offset }
    }
}

/**
 * The time axis of [data]'s bar chart: from the start to the last bar (with some days around it by month),
 * reaching the compared period's last bar too. Port of the x axis of `getCommonOptions`.
 */
fun barChartRange(data: EnergyData, zone: ZoneId): Pair<Long, Long> {
    val period = suggestedPeriod(data.period, fine = false)
    val start = data.period.start.atStartOfDay(zone)
    val end = data.period.endInstant(zone).atZone(zone)
    var max = suggestedMax(period, end)
    data.comparePeriod?.let { compare ->
        val compareEnd = compare.endInstant(zone).toEpochMilli()
        val transformed = Instant.ofEpochMilli(compareTransform(data, zone)(compareEnd)).atZone(zone)
        if (transformed.isAfter(max)) max = suggestedMax(period, transformed)
    }
    return if (period == StatisticPeriod.MONTH) {
        start.minusDays(MONTH_AXIS_PADDING_DAYS).toInstant().toEpochMilli() to
            max.plusDays(MONTH_AXIS_PADDING_DAYS).toInstant().toEpochMilli()
    } else {
        start.toInstant().toEpochMilli() to max.toInstant().toEpochMilli()
    }
}

/**
 * Where the time axis of bars ends: at the end for 5-minute bars, half past the last hour for hourly ones, the
 * start of the last day or month for daily or monthly ones. Port of `getSuggestedMax`.
 */
fun suggestedMax(period: StatisticPeriod, end: ZonedDateTime): ZonedDateTime = when (period) {
    StatisticPeriod.FIVE_MINUTES -> end
    StatisticPeriod.HOUR -> end.withMinute(HALF_HOUR).truncatedTo(ChronoUnit.MINUTES)
    StatisticPeriod.DAY, StatisticPeriod.MONTH -> {
        // Around DST changes the end can be 0:59 instead of 23:59
        val day = (if (end.hour == 0) end.minusHours(1) else end).truncatedTo(ChronoUnit.DAYS)
        if (period == StatisticPeriod.MONTH) day.withDayOfMonth(1) else day
    }
}

/** The fraction digits the y axis needs for values from [min] to [max]. Port of `computeYAxisFractionDigits`. */
fun yAxisFractionDigits(min: Double, max: Double): Int {
    val range = max - min
    if (!range.isFinite() || range <= 0) return 1
    return max(0, ceil(-log10(range / TEN)).toInt())
}

private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
private const val HOUR_MS = 60 * 60 * 1000L
private const val MONTH_AXIS_PADDING_DAYS = 5L
private const val HALF_HOUR = 30
private const val TEN = 10.0
