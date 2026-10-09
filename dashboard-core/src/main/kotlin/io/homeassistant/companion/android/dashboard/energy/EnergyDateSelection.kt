package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.time.DayOfWeek
import java.time.LocalDate

// What the energy date selection shows and offers. Port of frontend@20260624.6
// src/panels/lovelace/components/hui-energy-period-selector.ts and src/common/datetime/calc_date_range.ts.

/** The ranges the date picker offers, by their translation key (`ui.components.date-range-picker.ranges.*`). */
enum class EnergyRangePreset(val key: String) {
    TODAY("today"),
    YESTERDAY("yesterday"),
    THIS_WEEK("this_week"),
    THIS_MONTH("this_month"),
    THIS_QUARTER("this_quarter"),
    THIS_YEAR("this_year"),
    LAST_7_DAYS("now-7d"),
    LAST_30_DAYS("now-30d"),
    LAST_365_DAYS("now-365d"),
    LAST_12_MONTHS("now-12m"),
    ;

    /**
     * The days of this range on [today], as whole days: the picker sets the start of the first day and the end of
     * the last, so "the last 7 days" also includes today. Port of `calcDateRange`.
     */
    fun period(today: LocalDate, firstDayOfWeek: DayOfWeek): EnergyPeriod = when (this) {
        TODAY -> EnergyPeriod.day(today)
        YESTERDAY -> EnergyPeriod.day(today.minusDays(1))
        THIS_WEEK -> EnergyPeriod.week(today, firstDayOfWeek)
        THIS_MONTH -> EnergyPeriod.month(today)
        THIS_QUARTER -> EnergyPeriod.quarter(today)
        THIS_YEAR -> EnergyPeriod(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
        LAST_7_DAYS -> EnergyPeriod(today.minusDays(WEEK_DAYS), today)
        LAST_30_DAYS -> EnergyPeriod(today.minusDays(MONTH_DAYS), today)
        LAST_365_DAYS -> EnergyPeriod(today.minusDays(YEAR_DAYS), today)
        LAST_12_MONTHS -> EnergyPeriod.lastTwelveMonths(today)
    }
}

/** How the date selection names a period: "Oct 9", "September", "2026", with the year below when not this one. */
data class EnergyPeriodTitle(val title: String, val subtitle: String?)

/** The title of [period] on [today]. Port of the date range part of `HuiEnergyPeriodSelector.render`. */
fun DisplayFormats.energyPeriodTitle(period: EnergyPeriod, today: LocalDate): EnergyPeriodTitle {
    val range = period.simpleRange
    fun part(date: LocalDate, part: DatePart) = datePart(date, part)
    val title = when (range) {
        SimpleRange.YEAR -> part(period.start, DatePart.YEAR)
        SimpleRange.TWELVE_MONTHS, SimpleRange.MONTHS, SimpleRange.QUARTER ->
            "${part(period.start, DatePart.MONTH_SHORT)}$EN_DASH${part(period.end, DatePart.MONTH_SHORT)}"
        SimpleRange.MONTH -> part(period.start, DatePart.MONTH)
        SimpleRange.DAY -> part(period.start, DatePart.DAY_MONTH_SHORT)
        SimpleRange.OTHER ->
            "${part(period.start, DatePart.DAY_MONTH_SHORT)}$EN_DASH${part(period.end, DatePart.DAY_MONTH_SHORT)}"
    }
    val showStartYear = period.start.year != today.year
    val showBothYears = period.end.year != period.start.year
    val subtitle = if (range != SimpleRange.YEAR && (showStartYear || showBothYears)) {
        part(period.start, DatePart.YEAR) + if (showBothYears) EN_DASH + part(period.end, DatePart.YEAR) else ""
    } else {
        null
    }
    return EnergyPeriodTitle(title, subtitle)
}

private const val EN_DASH = "–"
private const val WEEK_DAYS = 7L
private const val MONTH_DAYS = 30L
private const val YEAR_DAYS = 365L
