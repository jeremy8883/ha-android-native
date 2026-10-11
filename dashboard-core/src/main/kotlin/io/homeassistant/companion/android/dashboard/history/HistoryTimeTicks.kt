package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.energy.StatisticPeriod
import io.homeassistant.companion.android.dashboard.energy.timeTicks
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The times a history chart's axis from [start] to [end] (epoch milliseconds) has labels at, about [maxLabels] of
 * them: whole minutes, as many apart as fit, over a span shorter than three hours (an entity created a few minutes
 * ago, as the frontend's time axis picks minutes then), else hours.
 */
fun DisplayFormats.historyTimeTicks(start: Long, end: Long, maxLabels: Int): List<Long> = when {
    end - start > DAYS_SPAN_MILLIS -> dayTicks(start, end, maxLabels)
    end - start >= SHORT_SPAN_MILLIS -> timeTicks(start, end, StatisticPeriod.HOUR, maxLabels)
    else -> minuteTicks(start, end, maxLabels)
}

/** Whole minutes from [start] to [end], as many apart as fit [maxLabels]. */
private fun DisplayFormats.minuteTicks(start: Long, end: Long, maxLabels: Int): List<Long> {
    val first = Instant.ofEpochMilli(start).atZone(zone).truncatedTo(ChronoUnit.MINUTES)
    val minutes = generateSequence(first) { it.plusMinutes(1) }
        .takeWhile { it.toInstant().toEpochMilli() <= end }
        .filter { it.toInstant().toEpochMilli() >= start }
        .toList()
    val step = MINUTE_STEPS.firstOrNull { (minutes.size + it - 1) / it <= maxLabels } ?: MINUTE_STEPS.last()
    return minutes.filter { it.minute % step == 0 }.map { it.toInstant().toEpochMilli() }
}

/** Local midnights from [start] to [end], as many days apart as fit [maxLabels]. */
private fun DisplayFormats.dayTicks(start: Long, end: Long, maxLabels: Int): List<Long> {
    val first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val days = generateSequence(first) { it.plusDays(1) }
        .map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
        .takeWhile { it <= end }
        .filter { it >= start }
        .toList()
    val step = DAY_STEPS.firstOrNull { (days.size + it - 1) / it <= maxLabels } ?: DAY_STEPS.last()
    return days.filterIndexed { index, _ -> index % step == 0 }
}

/** A time axis label, [bold] for the start of a day, month or year. */
data class TimeAxisLabel(val text: String, val bold: Boolean = false)

/**
 * The label of a history chart's time axis at [tick] over a span of [spanMillis]: months over a quarter, dates over
 * a week, weekdays over two days, else times (with seconds under five minutes), a day's start by its date in bold.
 * Port of `formatTimeLabel` (frontend@20260624.6 src/components/chart/axis-label.ts).
 */
fun DisplayFormats.historyTimeLabel(tick: Long, spanMillis: Long): TimeAxisLabel {
    val instant = Instant.ofEpochMilli(tick)
    val time = instant.atZone(zone)
    val date = time.toLocalDate()
    val days = spanMillis / DAY_MILLIS.toDouble()
    val minutes = spanMillis / MINUTE_MILLIS.toDouble()
    return when {
        days > QUARTER_DAYS -> if (time.monthValue == 1) {
            TimeAxisLabel(datePart(date, DatePart.MONTH_YEAR), bold = true)
        } else {
            TimeAxisLabel(datePart(date, DatePart.MONTH))
        }
        days > WEEK_DAYS -> TimeAxisLabel(datePart(date, DatePart.DAY_MONTH_SHORT), bold = time.dayOfMonth == 1)
        days > 2 -> TimeAxisLabel(datePart(date, DatePart.WEEKDAY_SHORT))
        minutes > 0 && minutes < SECONDS_SPAN_MINUTES -> TimeAxisLabel(timeWithSeconds(instant))
        time.toLocalTime() == java.time.LocalTime.MIDNIGHT ->
            TimeAxisLabel(datePart(date, DatePart.DAY_MONTH_SHORT), bold = true)
        else -> TimeAxisLabel(time(instant))
    }
}

private const val DAYS_SPAN_MILLIS = 2 * 24 * 60 * 60 * 1000L
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val MINUTE_MILLIS = 60 * 1000L
private const val QUARTER_DAYS = 88
private const val WEEK_DAYS = 7
private const val SECONDS_SPAN_MINUTES = 5
private val DAY_STEPS = listOf(1, 2, 3, 7, 14, 30)
private const val SHORT_SPAN_MILLIS = 3 * 60 * 60 * 1000L
private val MINUTE_STEPS = listOf(1, 2, 5, 10, 15, 30, 60)
