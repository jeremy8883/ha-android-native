package io.homeassistant.companion.android.dashboard.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartAxisTest {
    @Test
    fun `Given a flat line with rounding noise when labelling the y axis then it is flat with one decimal`() {
        // Upstream asks for 16 digits here; the native axis treats the noise as a flat line
        assertEquals(1, yAxisFractionDigits(13.199999999999998, 13.2))
        assertEquals(niceTicks(13.2, 13.2, scale = true), niceTicks(13.199999999999998, 13.2, scale = true))
    }

    @Test
    fun `Given a small real range when labelling the y axis then its digits are upstream's`() {
        assertEquals(2, yAxisFractionDigits(21.0, 21.8))
        assertEquals(5, yAxisFractionDigits(0.0001, 0.0009))
    }
}
