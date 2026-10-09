package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the energy gauges against what the real frontend's gauge cards showed. */
class EnergyGaugesGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the gauges then they show what the frontend's showed`(): List<DynamicTest> = energy.periods.flatMap { recorded ->
        EnergyGaugeType.entries.map { type ->
            DynamicTest.dynamicTest("${recorded.name} ${type.cardType}") {
                val expected = recorded.card(type.cardType)

                val gauge = energy.fixture.hass.energyGauge(type, recorded.data)

                val message = expected.string("message")
                val value = expected.number("value")
                when {
                    message != null -> assertEquals(EnergyGaugeModel.Message(message), gauge)
                    value == null -> assertEquals(EnergyGaugeModel.Hidden, gauge)
                    else -> {
                        gauge as EnergyGaugeModel.Gauge
                        assertEquals(value, gauge.value, TOLERANCE)
                        assertEquals(expected.string("text"), gauge.text)
                        assertEquals(expected.string("name"), gauge.name)
                        assertEquals(expected.boolean("needle") == true, gauge.needle)
                        assertEquals(expected.string("color"), gauge.severity?.let { "var(--${it.variable})" })
                    }
                }
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
