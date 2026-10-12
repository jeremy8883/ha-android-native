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

    @Test
    fun `Given the large untracked series hidden when labelling the y axis then the digits follow the shown devices`() {
        val device = series("device", 0.05, 0.12, 0.31)
        val untracked = series(UNTRACKED_ID, 0.4, 2.5, 6.0)
        val all = listOf(device, untracked)
        val chart = EnergyBarChart(
            series = all,
            xMin = 0,
            xMax = 3,
            period = StatisticPeriod.HOUR,
            yFractionDigits = yAxisFractionDigits(0.05, 6.0),
            unit = "kWh",
            compare = false,
        )

        assertEquals(1, chart.showing(all).yFractionDigits)
        assertEquals(2, chart.showing(listOf(device)).yFractionDigits)
    }

    @Test
    fun `Given every series shown when labelling the y axis then the digits are the chart's own`() {
        assertEquals(3, shownYAxisFractionDigits(allDigits = 3, anyHidden = false) { listOf(1.0, 100.0) })
        assertEquals(0, shownYAxisFractionDigits(allDigits = 3, anyHidden = true) { listOf(1.0, 100.0) })
    }

    private fun series(id: String, vararg values: Double) = EnergyBarSeries(
        id = id,
        name = id,
        kind = DEVICE_KIND,
        colorIndex = 0,
        compare = false,
        order = 0,
        points = values.mapIndexed { index, y -> EnergyBarPoint(index.toLong(), y, index.toLong()) },
    )
}
