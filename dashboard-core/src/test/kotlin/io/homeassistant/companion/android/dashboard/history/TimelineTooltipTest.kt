package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import java.time.ZoneOffset
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TimelineTooltipTest {

    private val formats = JdkDisplayFormats(Locale.US, ZoneOffset.UTC)
    private val band = TimelineBand(
        start = 1_000.0,
        end = 3_723_000.0,
        state = "on",
        label = "On",
        color = TimelineColor(emptyList(), null, null, "on"),
    )
    private val timeline = TimelineChart("light.kitchen", "Kitchen", listOf(band))

    @Test
    fun `Given durations when formatting them then they read like millisecondsToDuration`() {
        assertEquals("1:02:03", millisecondsToDuration(3_723_000))
        assertEquals("2:03", millisecondsToDuration(123_000))
        assertEquals("3", millisecondsToDuration(3_000))
        assertEquals("3.250", millisecondsToDuration(3_250))
        assertEquals("0.005", millisecondsToDuration(5))
        assertNull(millisecondsToDuration(0))
    }

    @Test
    fun `Given a named timeline when building a band's tooltip then it names the entity, both times with seconds and the duration`() {
        val tooltip = formats.timelineTooltip(timeline, band, showNames = true, durationLabel = "Duration")

        assertEquals("Kitchen", tooltip.title)
        assertEquals("On", tooltip.state)
        assertEquals("January 1, 1970 at 12:00:01 AM", tooltip.start.replace(' ', ' '))
        assertEquals("January 1, 1970 at 1:02:03 AM", tooltip.end.replace(' ', ' '))
        assertEquals("Duration: 1:02:02", tooltip.duration)
    }

    @Test
    fun `Given an unnamed timeline when building a band's tooltip then it has no title`() {
        assertNull(formats.timelineTooltip(timeline, band, showNames = false, durationLabel = "Duration").title)
    }
}
