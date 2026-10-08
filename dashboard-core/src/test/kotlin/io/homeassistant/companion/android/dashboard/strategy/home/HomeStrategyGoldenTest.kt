package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import io.homeassistant.companion.android.dashboard.strategy.commonControlsSection
import io.homeassistant.companion.android.dashboard.strategy.expandView
import io.homeassistant.companion.android.dashboard.strategy.resolveStrategyView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
            val fixture = Fixture(variant)
            val expected = fixture.json("outputs/dashboard.json")
            assertJsonEquals(expected, fixture.hass.homeDashboard(HomeDashboardConfig.fromSystemData(fixture.homeSystemData)))
        }
    }

    @TestFactory
    fun `Given captured server data when generating area views then they match the frontend`(): List<DynamicTest> = VARIANTS.flatMap { variant ->
        val fixture = Fixture(variant)
        fixture.json("outputs/dashboard.json").objects("views")
            .filter { it.obj("strategy")?.string("type") == "home-area" }
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
        val fixture = Fixture(variant)
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

    private class Fixture(variant: String) {
        private val root = "/fixtures/home/$variant/"

        fun json(path: String): JsonObject = Json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream(root + path)) { "Missing fixture $root$path" }.reader().readText(),
        ).jsonObject

        private fun wsResult(name: String): JsonElement = checkNotNull(json("ws/$name.json")["result"]) { "No result in $name" }

        /** Answers to the WebSocket calls the strategies made while the frontend generated them. */
        val strategyData: StrategyData = run {
            val calls = Json.parseToJsonElement(
                checkNotNull(javaClass.getResourceAsStream(root + "ws/strategy-calls.json")).reader().readText(),
            ).jsonArray.map { it.jsonObject }
            fun result(type: String) = calls.firstOrNull { it.obj("request")?.string("type") == type }?.get("result")
            StrategyData(
                energyPrefs = result("energy/get_prefs") as? JsonObject,
                commonControls = (result("usage_prediction/common_control") as? JsonObject)
                    ?.get("entities")?.jsonArray?.mapNotNull { it.stringOrNull },
            )
        }

        val homeSystemData: JsonObject? = wsResult("frontend-get_system_data-home").jsonObject.obj("value")

        val hass: HassSnapshot = run {
            val user = wsResult("auth-current_user").jsonObject
            val config = wsResult("get_config").jsonObject
            val strings = json("inputs/translations.json").obj("strings") ?: JsonObject(emptyMap())
            HassSnapshot(
                states = parseStates(wsResult("get_states").jsonArray),
                registries = Registries(
                    entities = parseEntityRegistryDisplay(wsResult("config-entity_registry-list_for_display").jsonObject),
                    devices = parseDeviceRegistry(wsResult("config-device_registry-list").jsonArray),
                    areas = parseAreaRegistry(wsResult("config-area_registry-list").jsonArray),
                    floors = parseFloorRegistry(wsResult("config-floor_registry-list").jsonArray),
                ),
                user = HassUser(
                    id = user.string("id").orEmpty(),
                    name = user.string("name"),
                    isAdmin = user.boolean("is_admin") == true,
                    isOwner = user.boolean("is_owner") == true,
                ),
                config = HassConfig(
                    state = config.string("state"),
                    recoveryMode = config.boolean("recovery_mode") == true,
                    version = config.string("version"),
                    components = config["components"]?.jsonArray?.mapNotNull { it.stringOrNull }?.toSet().orEmpty(),
                ),
                panels = wsResult("get_panels").jsonObject.keys,
                // The capture records the flat bundle strings the strategies used
                localize = Localize { key -> strings[key]?.stringOrNull.orEmpty() },
            )
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

    private fun JsonElement.sorted(): JsonElement = when (this) {
        is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.sorted() })
        is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(map { it.sorted() })
        else -> this
    }

    private companion object {
        val VARIANTS = listOf("test-instance", "test-instance-nonadmin")
    }
}
