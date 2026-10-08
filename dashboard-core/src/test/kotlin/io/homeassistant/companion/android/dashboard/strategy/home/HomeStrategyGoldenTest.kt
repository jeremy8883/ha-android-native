package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.golden.sorted
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.commonControlsSection
import io.homeassistant.companion.android.dashboard.strategy.expandView
import io.homeassistant.companion.android.dashboard.strategy.resolveStrategyView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests against output of the real frontend (20260624.6) captured by tools/golden/capture.mjs on
 * the local test instance: the Kotlin port, fed the same raw WebSocket data, must produce identical JSON.
 */
class HomeStrategyGoldenTest {

    @TestFactory
    fun `Given captured server data when generating the home dashboard then it matches the frontend`(): List<DynamicTest> = VARIANTS.map { variant ->
        DynamicTest.dynamicTest(variant) {
            val fixture = GoldenFixture(variant)
            val expected = fixture.json("outputs/dashboard.json")
            assertJsonEquals(expected, fixture.hass.homeDashboard(HomeDashboardConfig.fromSystemData(fixture.homeSystemData)))
        }
    }

    @TestFactory
    fun `Given captured server data when generating area, media and other devices views then they match the frontend`(): List<DynamicTest> = VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        fixture.json("outputs/dashboard.json").objects("views")
            .filter { it.obj("strategy")?.string("type") in SIMPLE_VIEW_STRATEGIES }
            .map { view ->
                val path = view.string("path")
                DynamicTest.dynamicTest("$variant $path") {
                    val expected = fixture.json("outputs/views/$path.json")
                    val actual = fixture.hass.resolveStrategyView(ViewConfig(view))?.json
                    assertJsonEquals(expected, actual)
                }
            }
    }

    @TestFactory
    fun `Given captured server data when generating the overview then view, section and expansion match the frontend`(): List<DynamicTest> = VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val overview = ViewConfig(fixture.json("outputs/dashboard.json").objects("views").first { it.string("path") == "overview" })
        listOf(
            DynamicTest.dynamicTest("$variant view") {
                assertJsonEquals(
                    fixture.json("outputs/views/overview.json"),
                    fixture.hass.resolveStrategyView(overview, fixture.strategyData)?.json,
                )
            },
            DynamicTest.dynamicTest("$variant common-controls section") {
                val section = fixture.json("outputs/sections/overview/0-common-controls.json")
                val input = section.obj("input")!!
                val generated = fixture.hass.commonControlsSection(input.obj("strategy")!!, fixture.strategyData.commonControls)
                // The captured output is merged like generateLovelaceSectionStrategy: {...base, ...generated}
                assertJsonEquals(section.obj("output"), JsonObject(input.filterKeys { it != "strategy" } + generated))
            },
            DynamicTest.dynamicTest("$variant expanded") {
                val expected = fixture.json("outputs/expanded.json").objects("views").first { it.string("path") == "overview" }
                assertJsonEquals(expected, fixture.hass.expandView(overview, fixture.strategyData)?.json)
            },
        )
    }

    private fun assertJsonEquals(expected: JsonElement?, actual: JsonElement?) {
        val pretty = Json { prettyPrint = true }
        // Compare as pretty text so failures show a readable diff; key order is normalised first
        assertEquals(
            expected?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
            actual?.let { pretty.encodeToString(JsonElement.serializer(), it.sorted()) },
        )
    }

    private companion object {
        val VARIANTS = GoldenFixture.VARIANTS

        /** View strategies that need no data beyond the snapshot. */
        val SIMPLE_VIEW_STRATEGIES = setOf("home-area", "home-media-players", "home-other-devices")
    }
}
