package io.homeassistant.companion.android.dashboard.energy

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnergyPeriodTest {

    private fun date(text: String) = LocalDate.parse(text)

    private fun period(start: String, end: String) = EnergyPeriod(date(start), date(end))

    @Test
    fun `Given periods when naming their range then days, months, quarters and years are recognised`() {
        assertEquals(SimpleRange.DAY, period("2026-02-03", "2026-02-03").simpleRange)
        assertEquals(SimpleRange.MONTH, period("2026-02-01", "2026-02-28").simpleRange)
        assertEquals(SimpleRange.QUARTER, period("2026-04-01", "2026-06-30").simpleRange)
        assertEquals(SimpleRange.MONTHS, period("2026-05-01", "2026-07-31").simpleRange)
        assertEquals(SimpleRange.YEAR, period("2026-01-01", "2026-12-31").simpleRange)
        assertEquals(SimpleRange.TWELVE_MONTHS, period("2025-11-01", "2026-10-31").simpleRange)
        assertEquals(SimpleRange.OTHER, period("2026-02-03", "2026-02-09").simpleRange)
    }

    @Test
    fun `Given a period when shifting it then months move by months and days by days`() {
        assertEquals(period("2026-03-01", "2026-03-31"), period("2026-02-01", "2026-02-28").shift(forward = true))
        assertEquals(period("2025-01-01", "2025-12-31"), period("2026-01-01", "2026-12-31").shift(forward = false))
        assertEquals(period("2026-02-10", "2026-02-16"), period("2026-02-03", "2026-02-09").shift(forward = true))
    }

    @Test
    fun `Given a period when comparing it then the previous one has the same length`() {
        assertEquals(period("2026-01-01", "2026-01-31"), period("2026-02-01", "2026-02-28").previous())
        assertEquals(period("2026-01-27", "2026-02-02"), period("2026-02-03", "2026-02-09").previous())
        assertEquals(period("2025-02-03", "2025-02-09"), period("2026-02-03", "2026-02-09").yearBefore())
    }

    @Test
    fun `Given a past period when going to now then the same kind of period includes today`() {
        val today = date("2026-10-09")
        assertEquals(EnergyPeriod.day(today), period("2026-02-03", "2026-02-03").current(today, DayOfWeek.SUNDAY))
        assertEquals(
            EnergyPeriod.week(today, DayOfWeek.SUNDAY),
            EnergyPeriod.week(date("2026-02-03"), DayOfWeek.SUNDAY).current(today, DayOfWeek.SUNDAY),
        )
        assertEquals(period("2026-10-01", "2026-10-31"), period("2026-02-01", "2026-02-28").current(today, DayOfWeek.SUNDAY))
        assertEquals(period("2026-10-03", "2026-10-09"), period("2026-02-01", "2026-02-07").current(today, DayOfWeek.MONDAY))
    }

    @Test
    fun `Given the first hour of the day when choosing the default period then it is yesterday unless live`() {
        val today = date("2026-10-09")
        assertEquals(EnergyPeriod.day(date("2026-10-08")), EnergyPeriod.default(today, hour = 0, midnightRollover = false))
        assertEquals(EnergyPeriod.day(today), EnergyPeriod.default(today, hour = 0, midnightRollover = true))
        assertEquals(EnergyPeriod.day(today), EnergyPeriod.default(today, hour = 1, midnightRollover = false))
    }
}
