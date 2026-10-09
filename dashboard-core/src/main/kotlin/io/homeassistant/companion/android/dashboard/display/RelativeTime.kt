package io.homeassistant.companion.android.dashboard.display

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * [from] relative to [to]: "in 10 hours", "tomorrow", "6 years ago".
 * Port of `relativeTime` (frontend@20260624.6 src/common/datetime/relative_time.ts).
 *
 * @param includeTense `false` gives just the amount ("5 days"), as `hui-timestamp-display` shows uptimes
 */
fun DisplayFormats.relativeTime(from: Instant, to: Instant, includeTense: Boolean = true): String {
    val (value, unit) = selectUnit(from, to)
    return if (includeTense) relative(value, unit) else duration(abs(value), unit)
}

/**
 * The unit and rounded amount to express `from - to` in.
 * Port of `selectUnit` (src/common/util/select-unit.ts) with its default thresholds; calendar comparisons are
 * made in [DisplayFormats.zone].
 */
internal fun DisplayFormats.selectUnit(from: Instant, to: Instant): Pair<Long, RelativeUnit> {
    val secs = (from.toEpochMilli() - to.toEpochMilli()) / MS_PER_SECOND
    val mins = secs / SECS_PER_MIN
    val hours = secs / SECS_PER_HOUR
    return when {
        abs(secs) < SECOND_THRESHOLD -> jsRound(secs) to RelativeUnit.SECOND
        abs(mins) < MINUTE_THRESHOLD -> jsRound(mins) to RelativeUnit.MINUTE
        abs(hours) < HOUR_THRESHOLD -> jsRound(hours) to RelativeUnit.HOUR
        else -> calendarUnit(from, to, hours)
    }
}

/** The calendar part of `selectUnit`, past the hour threshold: days, weeks, months or years apart. */
private fun DisplayFormats.calendarUnit(from: Instant, to: Instant, hours: Double): Pair<Long, RelativeUnit> {
    val fromDate = from.atZone(zone).toLocalDate()
    val toDate = to.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(toDate, fromDate)
    val weekStart = DayOfWeek.of(firstWeekday)
    val weeks = ChronoUnit.DAYS.between(toDate.startOfWeek(weekStart), fromDate.startOfWeek(weekStart)) / DAYS_PER_WEEK
    val years = (fromDate.year - toDate.year).toLong()
    val months = years * MONTHS_PER_YEAR + fromDate.monthValue - toDate.monthValue
    return when {
        days == 0L -> jsRound(hours) to RelativeUnit.HOUR
        abs(days) < DAY_THRESHOLD -> days to RelativeUnit.DAY
        weeks == 0L -> days to RelativeUnit.DAY
        abs(weeks) < WEEK_THRESHOLD -> weeks to RelativeUnit.WEEK
        months == 0L -> weeks to RelativeUnit.WEEK
        abs(months) < MONTH_THRESHOLD || years == 0L -> months to RelativeUnit.MONTH
        else -> years to RelativeUnit.YEAR
    }
}

private fun LocalDate.startOfWeek(firstDay: DayOfWeek): LocalDate = with(TemporalAdjusters.previousOrSame(firstDay))

private const val MS_PER_SECOND = 1000.0
private const val SECS_PER_MIN = 60
private const val SECS_PER_HOUR = 3600
private const val DAYS_PER_WEEK = 7
private const val MONTHS_PER_YEAR = 12
private const val SECOND_THRESHOLD = 59
private const val MINUTE_THRESHOLD = 59
private const val HOUR_THRESHOLD = 22
private const val DAY_THRESHOLD = 5
private const val WEEK_THRESHOLD = 4
private const val MONTH_THRESHOLD = 11
