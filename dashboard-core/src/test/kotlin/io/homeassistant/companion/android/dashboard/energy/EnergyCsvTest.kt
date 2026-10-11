package io.homeassistant.companion.android.dashboard.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyCsvTest {

    private val energy = EnergyFixture()

    @Test
    fun `Given a period's data when making its CSV then it has a column per period and a row per statistic, then the computed consumption`() {
        val data = energy.periods.first().data

        val lines = energyCsv(data, "EUR").trimEnd('\n').split('\n')

        val times = data.stats.values.flatMap { values -> values.map { it.start } }.toSortedSet()
        assertEquals(3 + times.size, lines.first().split(',').size)
        assertTrue(lines.first().startsWith("entity_id,type,unit,"))
        val grid = data.prefs.energySources.filterIsInstance<EnergySource.Grid>().first().statEnergyFrom!!
        val gridRow = lines.first { it.startsWith("$grid,grid_consumption,kWh,") }.split(',')
        val first = data.stats.getValue(grid).first()
        assertEquals(jsString(first.change!!), gridRow[3 + times.indexOf(first.start)])
        assertTrue(lines.any { it.startsWith(",calculated_consumed_grid,kWh,") })
        assertTrue(lines.all { it.split(',').size == 3 + times.size })
    }

    @Test
    fun `Given numbers when writing them like JavaScript then whole ones have no fraction and small ones no exponent`() {
        assertEquals("1", jsString(1.0))
        assertEquals("0.25", jsString(0.25))
        assertEquals("0.0001", jsString(0.0001))
        assertEquals("-3.5", jsString(-3.5))
        assertEquals("0", jsString(0.0))
    }
}
