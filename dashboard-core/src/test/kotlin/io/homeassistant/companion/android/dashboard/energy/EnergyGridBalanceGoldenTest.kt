package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the grid balance card against what the real frontend's card showed. */
class EnergyGridBalanceGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the grid balance then it shows what the frontend's card showed`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val expected = recorded.card("energy-grid-balance")

            val model = energy.fixture.hass.energyGridBalance(recorded.data, title = null)

            assertEquals(expected.string("title"), model.title)
            assertEquals(expected.string("imported"), model.imported)
            assertEquals(expected.string("exported"), model.exported)
            assertEquals(expected.string("net"), model.net)
            assertEquals(expected.string("netClass") == "consumption", model.consumption)
            assertEquals(percent(expected.string("left")), model.exportedShare * PERCENT, TOLERANCE)
            assertEquals(percent(expected.string("right")), model.importedShare * PERCENT, TOLERANCE)
            val net = expected.string(if (model.consumption) "netRight" else "netLeft")
            assertEquals(percent(net), model.netShare * PERCENT, TOLERANCE)
        }
    }

    /** A CSS width, which the browser rounds to 4 decimals. */
    private fun percent(css: String?) = css!!.removeSuffix("%").toDouble()

    private companion object {
        const val PERCENT = 100.0
        const val TOLERANCE = 1e-4
    }
}
