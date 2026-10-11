package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.time.Instant

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

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val MINUTE_MILLIS = 60 * 1000L
private const val QUARTER_DAYS = 88
private const val WEEK_DAYS = 7
private const val SECONDS_SPAN_MINUTES = 5
