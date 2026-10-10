package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryTimeTicksTest {
    private val formats = JdkDisplayFormats.DEFAULT

    private fun ticks(from: String, to: String, maxLabels: Int) = formats.historyTimeTicks(
        Instant.parse(from).toEpochMilli(),
        Instant.parse(to).toEpochMilli(),
        maxLabels,
    ).map { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalTime().toString() }

    @Test
    fun `Given a history of a few minutes when placing the axis labels then they fall on whole minutes`() {
        assertEquals(listOf("19:05", "19:10"), ticks("2026-10-10T19:03:20Z", "2026-10-10T19:12:40Z", maxLabels = 4))
    }

    @Test
    fun `Given a day of history when placing the axis labels then they stay on hours`() {
        assertEquals(listOf("00:00", "06:00", "12:00", "18:00"), ticks("2026-10-09T19:12:00Z", "2026-10-10T19:12:00Z", maxLabels = 4))
    }
}
