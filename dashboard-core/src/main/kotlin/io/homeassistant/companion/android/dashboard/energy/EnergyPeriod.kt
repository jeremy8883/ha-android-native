package io.homeassistant.companion.android.dashboard.energy

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The days the energy dashboard shows, [start] to [end] included, in the frontend's time zone. The period selector
 * only ever picks whole days (`startOfDay` to `endOfDay`), and so does the default "today".
 */
data class EnergyPeriod(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "The period ends before it starts: $start to $end" }
    }

    /** The instant the period starts in [zone] (`startOfDay`). */
    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

    /** The last millisecond of the period in [zone] (`endOfDay`). */
    fun endInstant(zone: ZoneId): Instant = end.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)

    /** date-fns `differenceInDays(end, start)` of the period's instants: whole days, so one less than its length. */
    val dayDifference: Long get() = ChronoUnit.DAYS.between(start, end)

    /** Whether the period is whole months: from a first day of a month to a last day of a month. */
    val isWholeMonths: Boolean get() = start.dayOfMonth == 1 && end == end.with(TemporalAdjusters.lastDayOfMonth())

    /** date-fns `differenceInMonths(end, start)`: the whole months between the period's instants. */
    val monthDifference: Long get() = ChronoUnit.MONTHS.between(start, end)

    /**
     * The kind of range the period selector names the period by. Port of `_simpleRange` (frontend@20260624.6
     * src/panels/lovelace/components/hui-energy-period-selector.ts).
     */
    val simpleRange: SimpleRange
        get() = when {
            dayDifference == 0L -> SimpleRange.DAY
            !isWholeMonths -> SimpleRange.OTHER
            monthDifference == 0L -> SimpleRange.MONTH
            monthDifference == 2L && (start.monthValue - 1) % MONTHS_IN_QUARTER == 0 -> SimpleRange.QUARTER
            monthDifference == MONTHS_IN_YEAR - 1L && start.year == end.year -> SimpleRange.YEAR
            monthDifference == MONTHS_IN_YEAR - 1L -> SimpleRange.TWELVE_MONTHS
            else -> SimpleRange.MONTHS
        }

    /**
     * The period of the same length just before ([forward] `false`) or after this one: whole months move by months,
     * days by days. Port of `shiftDateRange` (src/common/datetime/calc_date.ts) for whole-day periods.
     */
    fun shift(forward: Boolean): EnergyPeriod {
        val sign = if (forward) 1L else -1L
        return if (isWholeMonths) {
            val months = (monthDifference + 1) * sign
            EnergyPeriod(start.plusMonths(months), end.plusMonths(months).with(TemporalAdjusters.lastDayOfMonth()))
        } else {
            val days = (dayDifference + 1) * sign
            EnergyPeriod(start.plusDays(days), end.plusDays(days))
        }
    }

    /**
     * The period of the same kind that includes [today]: this month, quarter or year, the last months or days up to
     * today, or this week when the period is a week starting on [firstDayOfWeek]. Port of `_pickNow`.
     */
    fun current(today: LocalDate, firstDayOfWeek: DayOfWeek): EnergyPeriod = when (simpleRange) {
        SimpleRange.MONTH -> month(today)
        SimpleRange.QUARTER -> quarter(today)
        SimpleRange.YEAR -> EnergyPeriod(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
        SimpleRange.TWELVE_MONTHS -> lastTwelveMonths(today)
        SimpleRange.MONTHS -> EnergyPeriod(
            today.withDayOfMonth(1).minusMonths(monthDifference),
            today.with(TemporalAdjusters.lastDayOfMonth()),
        )
        SimpleRange.DAY, SimpleRange.OTHER -> if (this == week(end, firstDayOfWeek)) {
            week(today, firstDayOfWeek)
        } else {
            EnergyPeriod(today.minusDays(dayDifference), today)
        }
    }

    /**
     * The period just before this one to compare it with: as many months before when whole months, else as many
     * days. Port of the `CompareMode.PREVIOUS` branch of `getEnergyData` (src/data/energy.ts).
     */
    fun previous(): EnergyPeriod = if (isWholeMonths) {
        EnergyPeriod(start.minusMonths(monthDifference + 1), start.minusDays(1))
    } else {
        EnergyPeriod(start.minusDays(dayDifference + 1), start.minusDays(1))
    }

    /** The same days a year earlier. Port of the `CompareMode.YOY` branch of `getEnergyData`. */
    fun yearBefore(): EnergyPeriod = EnergyPeriod(start.minusYears(1), end.minusYears(1))

    companion object {
        /** Today. */
        fun day(date: LocalDate) = EnergyPeriod(date, date)

        /** The week of [date], starting on [firstDayOfWeek]. */
        fun week(date: LocalDate, firstDayOfWeek: DayOfWeek): EnergyPeriod {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return EnergyPeriod(start, start.plusDays(DAYS_IN_WEEK - 1L))
        }

        /** The month of [date]. */
        fun month(date: LocalDate) = EnergyPeriod(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))

        /** The quarter of [date]. */
        fun quarter(date: LocalDate): EnergyPeriod {
            val start = date.withDayOfMonth(1).withMonth(
                (date.monthValue - 1) / MONTHS_IN_QUARTER * MONTHS_IN_QUARTER + 1,
            )
            return EnergyPeriod(
                start,
                start.plusMonths(MONTHS_IN_QUARTER - 1L).with(TemporalAdjusters.lastDayOfMonth()),
            )
        }

        /** The twelve months up to the end of the month of [date] (`now-12m`). */
        fun lastTwelveMonths(date: LocalDate) = EnergyPeriod(
            date.withDayOfMonth(1).minusMonths(MONTHS_IN_YEAR - 1L),
            date.with(TemporalAdjusters.lastDayOfMonth()),
        )

        /**
         * The period the energy dashboard starts on: today, or yesterday during the first hour of the day when
         * today has no statistics yet, unless [midnightRollover] (the real-time "Now" view). Port of the default
         * period of `getEnergyDataCollection`.
         */
        fun default(today: LocalDate, hour: Int, midnightRollover: Boolean): EnergyPeriod =
            if (hour == 0 && !midnightRollover) day(today.minusDays(1)) else day(today)
    }
}

/** The kinds of ranges the period selector names a period by. */
enum class SimpleRange { DAY, MONTH, QUARTER, YEAR, TWELVE_MONTHS, MONTHS, OTHER }

/** The period to compare the shown one with, if any. Port of `CompareMode`. */
enum class CompareMode(val value: String) {
    PREVIOUS("previous"),
    YEAR_OVER_YEAR("yoy"),
}

private const val DAYS_IN_WEEK = 7
private const val MONTHS_IN_QUARTER = 3
private const val MONTHS_IN_YEAR = 12
