package io.homeassistant.companion.android.dashboard.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnergyDevicesHiddenTest {

    private val energy = EnergyFixture()

    @Test
    fun `Given a hidden device when computing the donut then it stays in the legend but leaves the slices and the total`() {
        val data = energy.periods.first().data
        val all = energy.fixture.hass.energyDevicesGraph(data, DevicesChartType.PIE)
        val device = all.slices.first()

        val model = energy.fixture.hass.energyDevicesGraph(data, DevicesChartType.PIE, hidden = setOf(device.id))

        assertEquals(all.slices.map { it.id }, model.legend.map { it.id })
        assertEquals(all.slices.map { it.id } - device.id, model.slices.map { it.id })
        assertEquals(all.total!! - device.value, model.total!!, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
