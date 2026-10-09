package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the Now tab's live sankeys and total badges against the frontend's. */
class RateSankeyGoldenTest {

    private val energy = EnergyFixture()

    // The live cards don't depend on the period, so one is enough
    private val recorded = energy.periods.first()

    @TestFactory
    fun `Given the live states when computing the power and water flow sankeys then they have the frontend's nodes and flows`(): List<DynamicTest> = recorded.json.obj("cards")!!.keys.filter { it.startsWith("power-sankey") || it.startsWith("water-flow-sankey") }
        .map { name ->
            DynamicTest.dynamicTest(name) {
                val expected = recorded.card(name)
                val hass = expected.hass()
                val grouped = name !in FLAT
                val sankey = if (name.startsWith("power")) {
                    hass.powerSankey(energy.prefs, grouped, grouped)
                } else {
                    hass.waterFlowSankey(energy.prefs, grouped, grouped)
                }

                if (expected["data"] is JsonObject) {
                    assertSankey(expected, sankey)
                } else {
                    assertTrue(sankey.nodes.none { it.value > 0 }, "Expected no data, got ${sankey.nodes}")
                }
            }
        }

    @TestFactory
    fun `Given the live states when showing the total badges then they read like the frontend's`(): List<DynamicTest> = listOf("power-total", "gas-total", "water-total").map { name ->
        DynamicTest.dynamicTest(name) {
            val expected = recorded.card(name)
            val hass = expected.hass()

            val text = when (name) {
                "power-total" -> hass.formats.powerTotal(hass.powerUse(energy.prefs))
                "gas-total" -> hass.formats.flowRate(hass.totalFlowRate(energy.prefs, UtilityType.GAS))
                else -> hass.formats.flowRate(hass.totalFlowRate(energy.prefs, UtilityType.WATER))
            }

            assertEquals(expected.string("text"), text)
        }
    }

    /** The fixture's hass with the states the frontend's card read. */
    private fun JsonObject.hass(): HassSnapshot {
        val fixture = energy.fixture.hass
        return fixture.copy(states = fixture.states + parseStates(JsonArray(obj("states")!!.values.toList())))
    }

    private companion object {
        /** The captures configured without `group_by_floor` and `group_by_area`, which default to true (capture.mjs). */
        val FLAT = setOf("power-sankey-evening")
    }
}
