package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the energy distribution card's values against what the real frontend's card showed. */
class EnergyDistributionGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the distribution then it shows what the frontend's card showed`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val expected = recorded.card("energy-distribution")

            val model = energy.fixture.hass.energyDistribution(recorded.data, energy.capturedAt)

            assertEquals(expected.string("lowCarbon"), model.lowCarbon)
            assertEquals(expected.string("solar"), model.solar)
            assertEquals(expected.string("gas"), model.gas)
            assertEquals(expected.string("water"), model.water)
            assertEquals(expected.string("gridReturn"), model.grid?.returned)
            assertEquals(expected.string("gridConsumption"), model.grid?.fromGrid)
            assertEquals(expected.string("home"), model.home)
            assertEquals(expected.string("homeLabel"), model.homeLabel)
            assertEquals(expected.string("batteryIn"), model.battery?.charged)
            assertEquals(expected.string("batteryOut"), model.battery?.discharged)
            assertEquals(expected.string("batterySoc")?.ifEmpty { null }, model.battery?.stateOfCharge)
            assertEquals(expected["waterBelow"].toString() == "true", model.waterBelow)
            assertRing(expected.objects("ring").associate { it.string("class") to it.string("dasharray") }, model)
            // Durations to the last bit can differ by the order sums are added in
            val flows = expected.objects("flows").associate {
                FLOW_CLASSES.getValue(it.string("class")!!) to it.string("dur")!!.removeSuffix("s").toDouble()
            }
            assertEquals(flows.keys, model.flows.keys)
            flows.forEach { (flow, seconds) -> assertEquals(seconds, model.flows.getValue(flow), TOLERANCE, "$flow") }
        }
    }

    private fun assertRing(expected: Map<String?, String?>, model: EnergyDistributionModel) {
        val ring = model.homeRing
        val actual = buildMap {
            ring?.solar?.let { put("solar", it) }
            ring?.battery?.let { put("battery", it) }
            ring?.lowCarbon?.let { put("low-carbon", it) }
            ring?.grid?.let { put("grid", it) }
        }
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (cls, dasharray) ->
            val dash = dasharray!!.substringBefore(' ').toDouble()
            assertEquals(dash, actual.getValue(cls!!) * CIRCUMFERENCE, TOLERANCE, "ring $cls")
        }
    }

    private companion object {
        const val CIRCUMFERENCE = 238.76104
        const val TOLERANCE = 1e-9

        // The frontend's dot classes by flow
        val FLOW_CLASSES = mapOf(
            "return" to DistributionFlow.SOLAR_TO_GRID,
            "solar" to DistributionFlow.SOLAR_TO_HOME,
            "grid" to DistributionFlow.GRID_TO_HOME,
            "battery-solar" to DistributionFlow.SOLAR_TO_BATTERY,
            "battery-house" to DistributionFlow.BATTERY_TO_HOME,
            "battery-from-grid" to DistributionFlow.GRID_TO_BATTERY,
            "battery-to-grid" to DistributionFlow.BATTERY_TO_GRID,
        )
    }
}
