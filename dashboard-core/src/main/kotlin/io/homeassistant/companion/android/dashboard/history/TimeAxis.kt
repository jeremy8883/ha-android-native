package io.homeassistant.companion.android.dashboard.history

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToLong

// The ticks of the history charts' time axis, where their labels and vertical grid lines are. Ports of ECharts
// 6.1.0's time scale (`calcNiceForTimeScale` and `createIntervalTicks`, lib/scale/Time.js), of how its axis hides
// labels (`fixMinMaxLabelShow` in lib/component/axis/AxisBuilder.js, `hideOverlap` in
// lib/label/labelLayoutHelper.js), and of the frontend's options for it (`minInterval` and `hideOverlap` in
// frontend@20260624.6 src/components/chart/ha-chart-base.ts `_createOptions`).

/**
 * A tick of a time axis at [time] (epoch ms). Ticks of coarser units (a day's start among hours) have a higher
 * [level], and their labels win when labels overlap. The axis's ends are ticks too when they fall between nice
 * times ([nice] `false`): they get a grid line but no label.
 */
data class TimeTick(val time: Long, val level: Int, val nice: Boolean = true)

/**
 * The ticks of a time axis showing [start] to [end] (epoch ms) in [zone], for a chart whose whole axis spans
 * [fullSpanMillis] (more than what is shown when zoomed in). Over more than two days the ticks are at least a day
 * apart, over a quarter or more at least 28 days, as the frontend sets `minInterval`.
 */
fun timeAxisTicks(start: Long, end: Long, zone: ZoneId, fullSpanMillis: Long = end - start): List<TimeTick> {
    if (end <= start) return emptyList()
    val dayDifference = (fullSpanMillis / MINUTE_MS).toDouble() / MINUTES_PER_DAY
    val minInterval = when {
        dayDifference >= QUARTER_DAYS -> QUARTER_MIN_INTERVAL
        dayDifference > 2 -> DAY_MS
        else -> null
    }
    val approxInterval = ((end - start).toDouble() / SPLIT_NUMBER).let {
        if (minInterval !=
            null
        ) {
            max(it, minInterval.toDouble())
        } else {
            it
        }
    }
    val index = SCALE_INTERVALS.indexOfFirst { it.second >= approxInterval }.let {
        if (it <
            0
        ) {
            SCALE_INTERVALS.lastIndex
        } else {
            it
        }
    }
    val minLevelUnit = SCALE_INTERVALS[max(index - 1, 0)].first
    return TickBuilder(start, end, zone, approxInterval, minLevelUnit).ticks()
}

/** Builds the ticks level by level, from years down, as `createIntervalTicks`. */
private class TickBuilder(
    val start: Long,
    val end: Long,
    val zone: ZoneId,
    val approxInterval: Double,
    /** The finest level when it is a primary unit; `null` for the secondary ones (half-day…), never reached. */
    val bottomUnit: TimeLevel?,
) {
    fun ticks(): List<TimeTick> {
        val levels = mutableListOf<List<Tick>>()
        var tickCount = 0
        val target = (end - start) / approxInterval
        var done = false
        val units = TimeLevel.entries.iterator()
        while (!done && units.hasNext()) {
            val unit = units.next()
            val level = levelTicks(unit, levels.lastOrNull().orEmpty())
            if (level.isNotEmpty()) {
                val lastLevelTickCount = tickCount
                val sorted = level.sortedBy { it.value }
                val deduplicated = sorted.filterIndexed { i, tick -> i == 0 || sorted[i - 1].value != tick.value }
                tickCount += deduplicated.count { it.value in start..end }
                // A level with far too many ticks is left out; one with enough, or the finest, is the last
                if (tickCount > target * TOO_MANY && lastLevelTickCount > target / TOO_MANY) {
                    done = true
                } else {
                    levels += deduplicated
                    done = tickCount > target || unit == bottomUnit
                }
            }
        }
        return withEnds(flatten(levels))
    }

    /** The ticks of [unit] between each pair of the coarser level's [last] ticks (the unit's own span at first). */
    private fun levelTicks(unit: TimeLevel, last: List<Tick>): List<Tick> {
        if (round(start, unit) == round(end, unit)) return emptyList()
        val bounds = last.ifEmpty { listOf(Tick(firstOfUnit(start, unit)), Tick(end)) }
        val out = mutableListOf<Tick>()
        for (i in 0 until bounds.lastIndex) {
            val from = bounds[i].value
            val to = bounds[i + 1].value
            if (from == to) continue
            val interval = unit.interval(approxInterval)
            if (to >= start && from <= end) addTicksInSpan(unit, interval, from, to, out)
        }
        return out
    }

    private fun addTicksInSpan(unit: TimeLevel, interval: Int, from: Long, to: Long, out: MutableList<Tick>) {
        var date = local(from)
        var time = from
        var count = 0
        while (time < to && time <= end && count++ < SAFE_LIMIT) {
            out += Tick(time)
            date = unit.add(date, interval)
            time = date.atZone(zone).toInstant().toEpochMilli()
        }
        // The tick after, to bound the next level's ticks; not one of this level's when past the end
        out += Tick(time, notAdd = time > end)
    }

    private fun flatten(levels: List<List<Tick>>): List<TimeTick> {
        val inExtent = levels.map { level ->
            level.filter { it.value in start..end && !it.notAdd }
        }.filter { it.isNotEmpty() }
        val maxLevel = inExtent.lastIndex
        val seen = HashSet<Long>()
        return inExtent.flatMapIndexed { i, level -> level.map { TimeTick(it.value, maxLevel - i) } }
            .filter { seen.add(it.time) }
            .sortedBy { it.time }
    }

    private fun withEnds(ticks: List<TimeTick>): List<TimeTick> = buildList {
        if (ticks.isEmpty() || ticks.first().time > start) add(TimeTick(start, 0, nice = false))
        addAll(ticks)
        if (ticks.isEmpty() || ticks.last().time < end) add(TimeTick(end, 0, nice = false))
    }

    private fun local(time: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), zone)

    /** Port of `roundTime`: [time] at the start of its [unit] (its year, month, day, hour…). */
    fun round(time: Long, unit: TimeLevel): Long = unit.truncate(local(time)).atZone(zone).toInstant().toEpochMilli()

    /** Port of `getFirstTimestampOfUnit`: the start of [unit]'s coarser unit around [time]. */
    fun firstOfUnit(time: Long, unit: TimeLevel): Long = round(time, TimeLevel.entries[max(0, unit.ordinal - 1)])
}

private data class Tick(val value: Long, val notAdd: Boolean = false)

/** ECharts' primary time units, coarsest first, with their tick intervals for an approximate interval. */
private enum class TimeLevel {
    YEAR {
        override fun interval(approx: Double) = max(1L, (approx / DAY_MS / DAYS_PER_YEAR).roundToLong()).toInt()
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusYears(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
    },
    MONTH {
        override fun interval(approx: Double): Int {
            val months = approx / (MONTH_DAYS * DAY_MS)
            return when {
                months > MONTHS_HALF_YEAR -> MONTHS_HALF_YEAR
                months > MONTHS_QUARTER -> MONTHS_QUARTER
                months > 2 -> 2
                else -> 1
            }
        }
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusMonths(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
    },
    DAY {
        // `getDateInterval`
        override fun interval(approx: Double): Int {
            val days = approx / DAY_MS
            return when {
                days > HALF_MONTH_DAYS -> HALF_MONTH_DAYS
                days > WEEK_THRESHOLD -> WEEK_DAYS
                days > HALF_WEEK_DAYS -> HALF_WEEK_STEP
                days > ONE_AND_HALF -> 2
                else -> 1
            }
        }
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusDays(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.DAYS)
    },
    HOUR {
        override fun interval(approx: Double): Int {
            val hours = approx / HOUR_MILLIS
            return when {
                hours > HALF_DAY_HOURS -> HALF_DAY_HOURS
                hours > QUARTER_DAY_HOURS -> QUARTER_DAY_HOURS
                hours > HOURS_THRESHOLD -> HOURS_STEP
                hours > 2 -> 2
                else -> 1
            }
        }
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusHours(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.HOURS)
    },
    MINUTE {
        override fun interval(approx: Double) = minutesAndSeconds(approx / MINUTE_MS)
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusMinutes(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.MINUTES)
    },
    SECOND {
        override fun interval(approx: Double) = minutesAndSeconds(approx / SECOND_MILLIS)
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusSeconds(amount.toLong())
        override fun truncate(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.SECONDS)
    },
    MILLISECOND {
        override fun interval(approx: Double) = max(1.0, niceFloor(approx)).toInt()
        override fun add(date: LocalDateTime, amount: Int): LocalDateTime = date.plusNanos(amount * NANOS_PER_MILLI)
        override fun truncate(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.MILLIS)
    },
    ;

    abstract fun interval(approx: Double): Int

    abstract fun add(date: LocalDateTime, amount: Int): LocalDateTime

    abstract fun truncate(date: LocalDateTime): LocalDateTime
}

/** `getMinutesAndSecondsInterval`. */
private fun minutesAndSeconds(count: Double): Int = MINUTE_SECOND_STEPS.firstOrNull { count > it } ?: 1

/** ECharts' `nice(value, round = true)` for the millisecond level. */
private fun niceFloor(value: Double): Double {
    if (value <= 0) return 1.0
    val exponent = kotlin.math.floor(kotlin.math.log10(value))
    val exp10 = Math.pow(10.0, exponent)
    val fraction = value / exp10
    val nice = when {
        fraction < 1.5 -> 1.0
        fraction < 2.5 -> 2.0
        fraction < 4 -> 3.0
        fraction < 7 -> 5.0
        else -> 10.0
    }
    return nice * exp10
}

/** A tick's label laid out on the axis: centred at [x] and [width] wide, px. */
data class TimeAxisLabelBox(val tick: TimeTick, val x: Float, val width: Float)

/**
 * Which of [labels] (in time order) are shown. Each label takes [margin] px more on either side (the axis labels'
 * `textMargin`). ECharts lays the axis out twice (an estimate for the grid, then for good), and its second pass
 * hides the labels in the order of the first's: those the first hid are placed first. So the result isn't the
 * coarsest ticks' labels but those between them, as the web shows.
 */
fun visibleTimeLabels(labels: List<TimeAxisLabelBox>, margin: Float): List<Boolean> {
    val estimate = layOutTimeLabels(labels, margin, suggestedHidden = List(labels.size) { false })
    return layOutTimeLabels(labels, margin, suggestedHidden = estimate.map { !it })
}

/**
 * One pass of ECharts' label layout: `fixMinMaxLabelShow` (ends that aren't nice times are hidden, as is an end or
 * its neighbour that a previous pass hid, else an end crossing its neighbour), then `hideOverlap`: the labels a
 * previous pass hid ([suggestedHidden]) first, then the coarsest ticks', each kept when it overlaps none kept.
 */
private fun layOutTimeLabels(
    labels: List<TimeAxisLabelBox>,
    margin: Float,
    suggestedHidden: List<Boolean>,
): List<Boolean> {
    val shown = BooleanArray(labels.size) { true }
    fun left(i: Int) = labels[i].x - labels[i].width / 2 - margin
    fun right(i: Int) = labels[i].x + labels[i].width / 2 + margin
    fun intersect(a: Int, b: Int, threshold: Float) = minOf(right(a), right(b)) - maxOf(left(a), left(b)) > threshold
    fun end(outmost: Int, inner: Int) {
        if (outmost !in labels.indices || inner !in labels.indices) return
        when {
            !labels[outmost].tick.nice || suggestedHidden[outmost] -> shown[outmost] = false
            suggestedHidden[inner] -> shown[inner] = false
            intersect(outmost, inner, MIN_MAX_TOUCH) -> shown[outmost] = false
        }
    }
    end(0, 1)
    end(labels.lastIndex, labels.lastIndex - 1)
    val kept = mutableListOf<Int>()
    labels.indices.filter { shown[it] }
        .sortedWith(compareByDescending<Int> { suggestedHidden[it] }.thenByDescending { labels[it].tick.level })
        .forEach { i -> if (kept.any { intersect(i, it, OVERLAP_TOUCH) }) shown[i] = false else kept += i }
    return shown.toList()
}

/** Labels sitting edge to edge don't overlap, within these px. */
private const val MIN_MAX_TOUCH = 0.1f
private const val OVERLAP_TOUCH = 0.05f

private const val SPLIT_NUMBER = 6
private const val TOO_MANY = 1.5
private const val SAFE_LIMIT = 3000
private const val SECOND_MILLIS = 1000L
private const val MINUTE_MS = 60 * SECOND_MILLIS
private const val HOUR_MILLIS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MILLIS
private const val MINUTES_PER_DAY = 24.0 * 60
private const val QUARTER_DAYS = 89
private const val QUARTER_MIN_INTERVAL = 28 * DAY_MS
private const val DAYS_PER_YEAR = 365.0
private const val MONTH_DAYS = 30.0
private const val MONTHS_HALF_YEAR = 6
private const val MONTHS_QUARTER = 3
private const val HALF_MONTH_DAYS = 16
private const val WEEK_THRESHOLD = 7.5
private const val WEEK_DAYS = 7
private const val HALF_WEEK_DAYS = 3.5
private const val HALF_WEEK_STEP = 4
private const val ONE_AND_HALF = 1.5
private const val HALF_DAY_HOURS = 12
private const val QUARTER_DAY_HOURS = 6
private const val HOURS_THRESHOLD = 3.5
private const val HOURS_STEP = 4
private const val NANOS_PER_MILLI = 1_000_000L
private val MINUTE_SECOND_STEPS = listOf(30, 20, 15, 10, 5, 2)

/**
 * ECharts' `scaleIntervals`: each level's unit and interval, ms; the level before the first not shorter than the
 * approximate interval is the finest. Quarter-day, half-day, half-week, week, quarter and half-year aren't primary
 * units (`null`), so never stop the levels.
 */
private val SCALE_INTERVALS: List<Pair<TimeLevel?, Double>> = listOf(
    TimeLevel.SECOND to SECOND_MILLIS.toDouble(),
    TimeLevel.MINUTE to MINUTE_MS.toDouble(),
    TimeLevel.HOUR to HOUR_MILLIS.toDouble(),
    null to HOUR_MILLIS * 6.0,
    null to HOUR_MILLIS * 12.0,
    TimeLevel.DAY to DAY_MS * 1.2,
    null to DAY_MS * 3.5,
    null to DAY_MS * 7.0,
    TimeLevel.MONTH to DAY_MS * 31.0,
    null to DAY_MS * 95.0,
    null to DAY_MS * DAYS_PER_YEAR / 2,
    TimeLevel.YEAR to DAY_MS * DAYS_PER_YEAR,
)
