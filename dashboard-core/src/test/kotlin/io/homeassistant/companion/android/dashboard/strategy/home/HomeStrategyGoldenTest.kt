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

    private class Fixture(variant: String) {
        private val root = "/fixtures/home/$variant/"

        fun json(path: String): JsonObject = Json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream(root + path)) { "Missing fixture $root$path" }.reader().readText(),
        ).jsonObject

        private fun wsResult(name: String): JsonElement = checkNotNull(json("ws/$name.json")["result"]) { "No result in $name" }

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
                    components = emptySet(),
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
