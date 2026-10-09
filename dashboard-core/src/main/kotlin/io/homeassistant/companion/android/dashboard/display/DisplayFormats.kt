package io.homeassistant.companion.android.dashboard.display

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** The parts of a date the period selector shows (src/common/datetime/format_date.ts). */
enum class DatePart {
    /** `formatDateVeryShort`: "Oct 9". */
    DAY_MONTH_SHORT,

    /** `formatDateMonth`: "October". */
    MONTH,

    /** `formatDateMonthShort`: "Oct". */
    MONTH_SHORT,

    /** `formatDateYear`: "2026". */
    YEAR,

    /** `formatDateMonthYear`: "October 2026". */
    MONTH_YEAR,

    /** `formatDateWeekdayVeryShortDate`: "Fri, Oct 9". */
    WEEKDAY_DAY_MONTH,

    /** `formatDateWeekdayShortDate`: "Fri, Oct 9, 2026". */
    WEEKDAY_DAY_MONTH_YEAR,

    /** `formatDateShort`: "Oct 9, 2026". */
    DAY_MONTH_YEAR_SHORT,
}

/** A unit of relative time, as `Intl.RelativeTimeFormat` takes it. */
enum class RelativeUnit { SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/**
 * The locale-dependent formatting primitives that the frontend gets from `Intl`, in the user's language and
 * time zone. Which value is formatted, with which options, is decided by the ported display logic.
 */
interface DisplayFormats {
    /** The time zone dates are shown in. */
    val zone: ZoneId

    /** The first day of the week, 1 (Monday) to 7 (Sunday). */
    val firstWeekday: Int

    /** `Intl.NumberFormat` with `minimumFractionDigits`/`maximumFractionDigits`, rounding half away from zero. */
    fun number(value: BigDecimal, minFractionDigits: Int, maxFractionDigits: Int): String

    /** `Intl.NumberFormat` with `style: "currency"`: "$12.00". */
    fun currency(value: BigDecimal, currency: Currency, minFractionDigits: Int, maxFractionDigits: Int): String

    /** `formatDate`: "January 1, 2020". */
    fun date(instant: Instant, zone: ZoneId = this.zone): String

    /** `formatDateTime`: "January 1, 2020 at 12:00 PM". */
    fun dateTime(instant: Instant, zone: ZoneId = this.zone): String

    /** `formatDateTimeWithSeconds`: "January 1, 2020 at 12:00:00 PM". */
    fun dateTimeWithSeconds(instant: Instant, zone: ZoneId = this.zone): String

    /** `formatTime`: "12:00 PM". */
    fun time(instant: Instant, zone: ZoneId = this.zone): String

    /** A date shown as [part] alone, such as "Oct 9" or "2026". */
    fun datePart(date: LocalDate, part: DatePart): String

    /** `Intl.RelativeTimeFormat` with `numeric: "auto"`: "tomorrow", "in 10 hours", "6 years ago". */
    fun relative(value: Long, unit: RelativeUnit): String

    /** `Intl.NumberFormat` with `style: "unit"` and `unitDisplay: "long"`: "5 days". */
    fun duration(value: Long, unit: RelativeUnit): String
}

/**
 * [DisplayFormats] from the JDK (CLDR) for [locale]. Relative times are English only; the app can supply a
 * platform implementation for other languages.
 */
class JdkDisplayFormats(private val locale: Locale, override val zone: ZoneId) : DisplayFormats {
    override val firstWeekday: Int = java.time.temporal.WeekFields.of(locale).firstDayOfWeek.value

    override fun number(value: BigDecimal, minFractionDigits: Int, maxFractionDigits: Int): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = minFractionDigits
        format.maximumFractionDigits = maxFractionDigits
        format.roundingMode = RoundingMode.HALF_UP
        return format.format(value)
    }

    override fun currency(
        value: BigDecimal,
        currency: Currency,
        minFractionDigits: Int,
        maxFractionDigits: Int,
    ): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        format.minimumFractionDigits = minFractionDigits
        format.maximumFractionDigits = maxFractionDigits
        format.roundingMode = RoundingMode.HALF_UP
        return format.format(value)
    }

    override fun date(instant: Instant, zone: ZoneId): String = format(DATE, instant, zone)

    // Browsers join a long date and a time with CLDR's "atTime" pattern, which the JDK does not expose
    override fun dateTime(instant: Instant, zone: ZoneId): String =
        date(instant, zone) + dateTimeJoiner + time(instant, zone)

    override fun dateTimeWithSeconds(instant: Instant, zone: ZoneId): String =
        date(instant, zone) + dateTimeJoiner + format(TIME_SECONDS, instant, zone)

    private val dateTimeJoiner = if (locale.language == Locale.ENGLISH.language) " at " else ", "

    override fun time(instant: Instant, zone: ZoneId): String = format(TIME, instant, zone)

    override fun datePart(date: LocalDate, part: DatePart): String =
        DATE_PARTS.getValue(part).withLocale(locale).format(date)

    private fun format(formatter: DateTimeFormatter, instant: Instant, zone: ZoneId): String =
        // Newer CLDR puts a narrow no-break space before AM/PM, browsers a plain one
        formatter.withLocale(locale).format(instant.atZone(zone)).replace(NARROW_NO_BREAK_SPACE, ' ')

    override fun relative(value: Long, unit: RelativeUnit): String {
        AUTO_PHRASES[unit]?.get(value)?.let { return it }
        val amount = duration(kotlin.math.abs(value), unit)
        return if (value < 0) "$amount ago" else "in $amount"
    }

    override fun duration(value: Long, unit: RelativeUnit): String {
        val name = unit.name.lowercase()
        return "${number(BigDecimal.valueOf(value), 0, 0)} ${if (value == 1L) name else "${name}s"}"
    }

    companion object {
        /** English with US conventions, in UTC: what the golden fixtures were captured with. */
        val DEFAULT = JdkDisplayFormats(Locale.US, ZoneOffset.UTC)

        private const val NARROW_NO_BREAK_SPACE = ' '
        private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        private val TIME_SECONDS = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
        private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        // English patterns of the frontend's `Intl` options; the month names follow the locale
        private val DATE_PARTS = mapOf(
            DatePart.DAY_MONTH_SHORT to DateTimeFormatter.ofPattern("MMM d"),
            DatePart.MONTH to DateTimeFormatter.ofPattern("LLLL"),
            DatePart.MONTH_SHORT to DateTimeFormatter.ofPattern("LLL"),
            DatePart.YEAR to DateTimeFormatter.ofPattern("y"),
            DatePart.MONTH_YEAR to DateTimeFormatter.ofPattern("LLLL y"),
            DatePart.WEEKDAY_DAY_MONTH to DateTimeFormatter.ofPattern("EEE, MMM d"),
            DatePart.WEEKDAY_DAY_MONTH_YEAR to DateTimeFormatter.ofPattern("EEE, MMM d, y"),
            DatePart.DAY_MONTH_YEAR_SHORT to DateTimeFormatter.ofPattern("MMM d, y"),
        )

        // CLDR English phrases that `numeric: "auto"` uses instead of numbers
        private val AUTO_PHRASES = mapOf(
            RelativeUnit.SECOND to mapOf(0L to "now"),
            RelativeUnit.MINUTE to mapOf(0L to "this minute"),
            RelativeUnit.HOUR to mapOf(0L to "this hour"),
            RelativeUnit.DAY to mapOf(-1L to "yesterday", 0L to "today", 1L to "tomorrow"),
            RelativeUnit.WEEK to mapOf(-1L to "last week", 0L to "this week", 1L to "next week"),
            RelativeUnit.MONTH to mapOf(-1L to "last month", 0L to "this month", 1L to "next month"),
            RelativeUnit.YEAR to mapOf(-1L to "last year", 0L to "this year", 1L to "next year"),
        )
    }
}
