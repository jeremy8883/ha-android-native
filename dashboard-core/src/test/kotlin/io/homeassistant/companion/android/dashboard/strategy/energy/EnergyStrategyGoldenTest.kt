package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.golden.sorted
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.resolveStrategyView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the energy dashboard and view strategies against output of the real frontend (20260624.6)
 * captured by tools/golden/capture.mjs on the local test instance, whose energy uses every kind of source.
 */
class EnergyStrategyGoldenTest {

    @TestFactory
    fun `Given captured energy preferences when generating the energy panel then it matches the frontend`(): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val dashboard = fixture.hass.energyDashboard(fixture.preferences(), fixture.hiddenCards())
        val expected = fixture.json("outputs/energy/dashboard.json")
        listOf(
            DynamicTest.dynamicTest("$variant dashboard") {
                assertJsonEquals(expected["views"], dashboard["views"])
            },
        ) + dashboard.objects("views").map { view ->
            val path = view.string("path")
            DynamicTest.dynamicTest("$variant view $path") {
                assertJsonEquals(
                    fixture.json("outputs/energy/views/$path.json"),
                    fixture.hass.resolveStrategyView(ViewConfig(view), fixture.strategyData)?.json,
                )
            }
        }
    }

    @Test
    fun `Given cards the user hid when generating then they and views without cards left are left out`() {
        val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
        val gasCards = listOf("gas.energy-gas-graph", "gas.energy-sources-table")

        val dashboard = fixture.hass.energyDashboard(fixture.preferences(), gasCards + "electricity.energy-sankey")

        val paths = dashboard.objects("views").map { it.string("path") }
        assertEquals(listOf("overview", "electricity", "water", "now"), paths)
        val electricity = dashboard.objects("views")[1]
        val view = fixture.hass.resolveStrategyView(ViewConfig(electricity), fixture.strategyData)!!.json
        val cardTypes = view.objects("sections").flatMap { it.objects("cards") }.map { it.string("type") }
        assert("energy-sankey" !in cardTypes) { "Hidden card still shown: $cardTypes" }
    }

    @Test
    fun `Given no energy preferences when generating then the setup wizard is the only view`() {
        val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())

        val dashboard = fixture.hass.energyDashboard(prefs = null, hiddenCards = null)

        assertEquals(
            listOf("custom:energy-setup-wizard-card"),
            dashboard.objects("views").single().objects("cards").map { it.string("type") },
        )
    }

    private fun GoldenFixture.preferences() = strategyData.energyPrefs?.let(EnergyPreferences::fromJson)

    private fun GoldenFixture.hiddenCards(): List<String>? = json("ws/frontend-get_system_data-energy.json")
        .obj("result")?.obj("value")?.array("hidden_cards")?.mapNotNull { it.stringOrNull }

    private fun assertJsonEquals(expected: JsonElement?, actual: JsonElement?) {
        val pretty = Json { prettyPrint = true }
        // Compare as pretty text so failures show a readable diff; key order is normalised first
        assertEquals(
            expected?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
            actual?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
        )
    }
}
