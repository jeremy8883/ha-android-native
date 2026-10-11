package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DateRangeTest {

    private val zone = ZoneId.of("Australia/Sydney")
    private val now = ZonedDateTime.of(2026, 10, 11, 12, 34, 56, 0, zone)

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant()

    private fun range(start: String, end: String) = DateRange(at(start), at(end))

    @Test
    fun `Given the time when opening the Logbook panel then it shows the last hour to the end of the next`() {
        assertEquals(range("2026-10-11T11:00", "2026-10-11T14:00"), DateRange.aroundNow(now))
    }

    @ParameterizedTest
    @CsvSource(
        "today, 2026-10-11T00:00, 2026-10-11T23:59:59.999",
        "yesterday, 2026-10-10T00:00, 2026-10-10T23:59:59.999",
        "this_week, 2026-10-05T00:00, 2026-10-11T23:59:59.999",
        "this_month, 2026-10-01T00:00, 2026-10-31T23:59:59.999",
        "this_year, 2026-01-01T00:00, 2026-12-31T23:59:59.999",
        "now-1h, 2026-10-11T11:34:56, 2026-10-11T12:34:56",
        "now-7d, 2026-10-04T12:34:56, 2026-10-11T12:34:56",
    )
    fun `Given a preset when picking it then its period is the frontend's`(key: String, start: String, end: String) {
        val preset = RangePreset.entries.single { it.key == key }
        assertEquals(range(start, end), preset.range(now, DayOfWeek.MONDAY))
    }

    @ParameterizedTest
    @CsvSource(
        // Whole months move by months, whole days by days, others by their length
        "2026-09-01T00:00, 2026-09-30T23:59:59.999, true, 2026-10-01T00:00, 2026-10-31T23:59:59.999",
        "2026-10-11T00:00, 2026-10-11T23:59:59.999, false, 2026-10-10T00:00, 2026-10-10T23:59:59.999",
        "2026-10-05T00:00, 2026-10-11T23:59:59.999, true, 2026-10-12T00:00, 2026-10-18T23:59:59.999",
        "2026-10-11T11:00, 2026-10-11T14:00, false, 2026-10-11T08:00, 2026-10-11T11:00",
    )
    fun `Given a period when moving to the next or previous then it moves as the frontend's arrows`(
        start: String,
        end: String,
        forward: Boolean,
        shiftedStart: String,
        shiftedEnd: String,
    ) {
        assertEquals(range(shiftedStart, shiftedEnd), range(start, end).shift(forward, zone))
    }

    @Test
    fun `Given a period when showing it in the field then each end reads as a short date and time`() {
        val formats = JdkDisplayFormats(Locale.US, zone)
        val today = LocalDate.of(2026, 10, 11)

        assertEquals(
            "Oct 11, 11:00 AM - \nOct 11, 2:00 PM",
            formats.dateRangeText(range("2026-10-11T11:00", "2026-10-11T14:00"), today),
        )
        assertEquals("Dec 31, 2025, 11:00 PM", formats.shortDateTime(at("2025-12-31T23:00"), today))
    }

    @Test
    fun `Given a preset's period when matching the presets then that preset is selected`() {
        val today = RangePreset.TODAY.range(now, DayOfWeek.MONDAY)

        assertEquals(RangePreset.TODAY, RangePreset.matching(today, RangePreset.BASIC, now, DayOfWeek.MONDAY))
        assertEquals(
            RangePreset.YESTERDAY,
            RangePreset.matching(range("2026-10-10T00:00", "2026-10-10T23:59:59.999"), RangePreset.BASIC, now, DayOfWeek.MONDAY),
        )
    }

    @Test
    fun `Given a period changed off a preset when matching the presets then none is selected`() {
        // Today, but from 1 AM
        val changed = range("2026-10-11T01:00", "2026-10-11T23:59:59.999")

        assertEquals(null, RangePreset.matching(changed, RangePreset.EXTENDED, now, DayOfWeek.MONDAY))
    }

    @Test
    fun `Given the last hour picked a little while ago when matching the presets then it is still the last hour`() {
        val picked = RangePreset.LAST_HOUR.range(now, DayOfWeek.MONDAY)

        assertEquals(RangePreset.LAST_HOUR, RangePreset.matching(picked, RangePreset.EXTENDED, now.plusSeconds(30), DayOfWeek.MONDAY))
        assertEquals(null, RangePreset.matching(picked, RangePreset.EXTENDED, now.plusMinutes(5), DayOfWeek.MONDAY))
    }
}
