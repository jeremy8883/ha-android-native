package io.homeassistant.companion.android.dashboard.display

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.floor

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
    if (abs(secs) < SECOND_THRESHOLD) return jsRound(secs) to RelativeUnit.SECOND
    val mins = secs / SECS_PER_MIN
    if (abs(mins) < MINUTE_THRESHOLD) return jsRound(mins) to RelativeUnit.MINUTE
    val hours = secs / SECS_PER_HOUR
    if (abs(hours) < HOUR_THRESHOLD) return jsRound(hours) to RelativeUnit.HOUR

    val fromDate = from.atZone(zone).toLocalDate()
    val toDate = to.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(toDate, fromDate)
    if (days == 0L) return jsRound(hours) to RelativeUnit.HOUR
    if (abs(days) < DAY_THRESHOLD) return days to RelativeUnit.DAY

    val weekStart = DayOfWeek.of(firstWeekday)
    val weeks = ChronoUnit.DAYS.between(toDate.startOfWeek(weekStart), fromDate.startOfWeek(weekStart)) / DAYS_PER_WEEK
    if (weeks == 0L) return days to RelativeUnit.DAY
    if (abs(weeks) < WEEK_THRESHOLD) return weeks to RelativeUnit.WEEK

    val years = (fromDate.year - toDate.year).toLong()
    val months = years * MONTHS_PER_YEAR + fromDate.monthValue - toDate.monthValue
    if (months == 0L) return weeks to RelativeUnit.WEEK
    if (abs(months) < MONTH_THRESHOLD || years == 0L) return months to RelativeUnit.MONTH
    return years to RelativeUnit.YEAR
}

private fun LocalDate.startOfWeek(firstDay: DayOfWeek): LocalDate = with(TemporalAdjusters.previousOrSame(firstDay))

/** JavaScript `Math.round`: halves round up, towards positive infinity. */
private fun jsRound(value: Double): Long = floor(value + HALF).toLong()

private const val MS_PER_SECOND = 1000.0
private const val SECS_PER_MIN = 60
private const val SECS_PER_HOUR = 3600
private const val DAYS_PER_WEEK = 7
private const val MONTHS_PER_YEAR = 12
private const val HALF = 0.5
private const val SECOND_THRESHOLD = 59
private const val MINUTE_THRESHOLD = 59
private const val HOUR_THRESHOLD = 22
private const val DAY_THRESHOLD = 5
private const val WEEK_THRESHOLD = 4
private const val MONTH_THRESHOLD = 11
