package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// The period the History and Logbook panels show, and its picker's presets and arrows. Ports of `calcDateRange`
// (frontend@20260624.6 src/common/datetime/calc_date_range.ts), `shiftDateRange` (src/common/datetime/calc_date.ts)
// and of the field of `ha-date-range-picker` (src/components/date-picker/ha-date-range-picker.ts).

/** A period from [start] to [end], both included. */
data class DateRange(val start: Instant, val end: Instant) {

    /**
     * The period before (or after, when [forward]) this one, as long as it: whole months move by months, whole days
     * by days, others by their length. Port of `shiftDateRange`.
     */
    fun shift(forward: Boolean, zone: ZoneId): DateRange {
        val sign = if (forward) 1L else -1L
        val startAt = start.atZone(zone)
        val endAt = end.atZone(zone)
        return when {
            startAt.isStartOfDay() && startAt.dayOfMonth == 1 && endAt.isEndOfDay() && endAt.isLastDayOfMonth() -> {
                val months = (ChronoUnit.MONTHS.between(startAt, endAt) + 1) * sign
                DateRange(
                    startAt.plusMonths(months).toInstant(),
                    endOfDay(endAt.plusMonths(months).with(TemporalAdjusters.lastDayOfMonth())).toInstant(),
                )
            }
            startAt.isStartOfDay() && endAt.isEndOfDay() -> {
                val days = (ChronoUnit.DAYS.between(startAt, endAt) + 1) * sign
                DateRange(startAt.plusDays(days).toInstant(), endAt.plusDays(days).toInstant())
            }
            else -> {
                val length = Duration.between(start, end).multipliedBy(sign)
                DateRange(start.plus(length), end.plus(length))
            }
        }
    }

    companion object {
        /** The Logbook panel's first period: from the start of the last hour to the end of the next one. */
        fun aroundNow(now: ZonedDateTime): DateRange {
            val hour = now.truncatedTo(ChronoUnit.HOURS)
            return DateRange(hour.minusHours(1).toInstant(), hour.plusHours(2).toInstant())
        }
    }
}

/** A period the picker offers by name, [key] naming it in `ui.components.date-range-picker.ranges`. */
enum class RangePreset(val key: String) {
    TODAY("today"),
    YESTERDAY("yesterday"),
    THIS_WEEK("this_week"),
    THIS_MONTH("this_month"),
    THIS_YEAR("this_year"),
    LAST_HOUR("now-1h"),
    LAST_12_HOURS("now-12h"),
    LAST_24_HOURS("now-24h"),
    LAST_7_DAYS("now-7d"),
    LAST_30_DAYS("now-30d"),
    ;

    /** The period as of [now], weeks starting on [firstDayOfWeek]. Port of `calcDateRange`. */
    fun range(now: ZonedDateTime, firstDayOfWeek: DayOfWeek): DateRange = when (this) {
        TODAY -> days(now.toLocalDate(), now.toLocalDate(), now.zone)
        YESTERDAY -> now.toLocalDate().minusDays(1).let { days(it, it, now.zone) }
        THIS_WEEK -> now.toLocalDate().with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let {
            days(it, it.plusDays(DAYS_IN_WEEK - 1), now.zone)
        }
        THIS_MONTH -> days(
            now.toLocalDate().withDayOfMonth(1),
            now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth()),
            now.zone,
        )
        THIS_YEAR -> days(
            now.toLocalDate().withDayOfYear(1),
            now.toLocalDate().with(TemporalAdjusters.lastDayOfYear()),
            now.zone,
        )
        LAST_HOUR -> before(now, Duration.ofHours(1))
        LAST_12_HOURS -> before(now, Duration.ofHours(HALF_DAY_HOURS))
        LAST_24_HOURS -> before(now, Duration.ofDays(1))
        LAST_7_DAYS -> before(now, Duration.ofDays(DAYS_IN_WEEK))
        LAST_30_DAYS -> before(now, Duration.ofDays(MONTH_DAYS))
    }

    companion object {
        /** The picker's presets (`RANGE_KEYS`). */
        val BASIC = listOf(TODAY, YESTERDAY, THIS_WEEK)

        /** The picker's presets with `extended-presets`, as the History panel's (`EXTENDED_RANGE_KEYS` added). */
        val EXTENDED =
            BASIC + listOf(THIS_MONTH, THIS_YEAR, LAST_HOUR, LAST_12_HOURS, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS)
    }
}

/**
 * The picker's field: "Oct 11, 1:00 AM - Oct 11, 4:00 AM", each end with its year when not in [today]'s year. On a
 * phone the second end goes on its own line, as the field is narrower than 459px.
 */
fun DisplayFormats.dateRangeText(range: DateRange, today: LocalDate): String =
    shortDateTime(range.start, today) + " - \n" + shortDateTime(range.end, today)

/** Port of `formatShortDateTime` ("Oct 11, 1:00 AM"), with the year when not in [today]'s (`…WithYear`). */
fun DisplayFormats.shortDateTime(instant: Instant, today: LocalDate): String {
    val date = instant.atZone(zone).toLocalDate()
    val part = if (date.year == today.year) DatePart.DAY_MONTH_SHORT else DatePart.DAY_MONTH_YEAR_SHORT
    return datePart(date, part) + ", " + time(instant)
}

private fun days(first: LocalDate, last: LocalDate, zone: ZoneId) =
    DateRange(first.atStartOfDay(zone).toInstant(), endOfDay(last.atStartOfDay(zone)).toInstant())

private fun before(now: ZonedDateTime, length: Duration) = DateRange(now.minus(length).toInstant(), now.toInstant())

/** The last millisecond of [day]'s date, as date-fns' `endOfDay`. */
private fun endOfDay(day: ZonedDateTime): ZonedDateTime =
    day.toLocalDate().plusDays(1).atStartOfDay(day.zone).minusNanos(MILLI_NANOS)

private fun ZonedDateTime.isStartOfDay() = this == toLocalDate().atStartOfDay(zone)

private fun ZonedDateTime.isEndOfDay() = this == endOfDay(this)

private fun ZonedDateTime.isLastDayOfMonth() = dayOfMonth == toLocalDate().lengthOfMonth()

private const val DAYS_IN_WEEK = 7L
private const val HALF_DAY_HOURS = 12L
private const val MONTH_DAYS = 30L
private const val MILLI_NANOS = 1_000_000L
