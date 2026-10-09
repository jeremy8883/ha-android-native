package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.golden.sorted
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.strategy.resolveStrategyView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the light, climate, security and maintenance view strategies against output of the real
 * frontend (20260624.6) captured by tools/golden/capture.mjs on the local test instance.
 */
class SummaryStrategyGoldenTest {

    @TestFactory
    fun `Given captured server data when generating a summary panel's view then it matches the frontend`(): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        SUMMARY_PANELS.map { panel ->
            DynamicTest.dynamicTest("$variant $panel") {
                // The view the panel's dashboard holds, resolved as the dashboards resolve views
                val view = ViewConfig(fixture.hass.summaryPanelDashboard(panel).objects("views").single())
                assertJsonEquals(fixture.json("outputs/panels/$panel.json"), fixture.hass.resolveStrategyView(view)?.json)
            }
        }
    }

    private fun assertJsonEquals(expected: JsonElement?, actual: JsonElement?) {
        val pretty = Json { prettyPrint = true }
        // Compare as pretty text so failures show a readable diff; key order is normalised first
        assertEquals(
            expected?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
            actual?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
        )
    }
}
