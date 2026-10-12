package io.homeassistant.companion.android.dashboard.display

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayFormatsTest {
    private val evening = Instant.parse("2026-10-12T21:05:09Z")
    private val morning = Instant.parse("2026-10-12T09:05:09Z")

    @Test
    fun `Given no clock choice when formatting times then the language's clock is used`() {
        val english = JdkDisplayFormats(Locale.US, ZoneOffset.UTC)
        val german = JdkDisplayFormats(Locale.GERMANY, ZoneOffset.UTC)

        assertEquals("9:05 PM", english.time(evening))
        assertTrue(english.amPm)
        assertEquals("21:05", german.time(evening))
        assertFalse(german.amPm)
    }

    @Test
    fun `Given a 24-hour clock when formatting English times then they read as hourCycle h23 does`() {
        val formats = JdkDisplayFormats(Locale.US, ZoneOffset.UTC, use24Hour = true)

        assertEquals("21:05", formats.time(evening))
        assertEquals("09:05", formats.time(morning))
        assertEquals("21:05:09", formats.timeWithSeconds(evening))
        assertEquals("October 12, 2026 at 21:05", formats.dateTime(evening))
        assertFalse(formats.amPm)
    }

    @Test
    fun `Given a 12-hour clock when formatting German times then they show AM and PM`() {
        val formats = JdkDisplayFormats(Locale.GERMANY, ZoneOffset.UTC, use24Hour = false)

        assertEquals("9:05 PM", formats.time(evening))
        assertEquals("9:05:09 PM", formats.timeWithSeconds(evening))
        assertTrue(formats.amPm)
    }
}
