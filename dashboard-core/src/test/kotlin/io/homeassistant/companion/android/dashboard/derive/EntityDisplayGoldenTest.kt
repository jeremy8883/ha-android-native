package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of per-entity display against the real frontend (20260624.6): for every entity of the test
 * instance, tools/golden/capture.mjs recorded what `ha-state-icon`, `formatEntityState`, `formatEntityName` and
 * the tile's `state-display` produced (outputs/entity-display.json).
 */
class EntityDisplayGoldenTest {

    @TestFactory
    fun `Given captured server data when resolving entity icons then they match ha-state-icon`(): List<DynamicTest> = perEntity { fixture, entityId, expected ->
        val captured = expected["icon"]
        val state = fixture.hass.states.getValue(entityId)
        if (captured is JsonObject) {
            // The frontend drew its built-in domain fallback, which it records without a name
            assertEquals(null, fixture.hass.stateIconOrNull(state), "expected the domain fallback")
        } else {
            assertEquals(captured?.stringOrNull, fixture.hass.entityIcon(entityId))
        }
    }

    @TestFactory
    fun `Given captured server data when naming entities then names match formatEntityName`(): List<DynamicTest> = perEntity { fixture, entityId, expected ->
        val state = fixture.hass.states.getValue(entityId)
        val actual = NAME_CONFIGS.mapValues { (_, config) -> fixture.hass.entityNameDisplay(state, config) }
        val captured = expected.obj("names")!!.mapValues { it.value.stringOrNull }
        assertEquals(captured, actual)
    }

    @TestFactory
    fun `Given captured server data when formatting states then they match formatEntityState`(): List<DynamicTest> = perEntity { fixture, entityId, expected ->
        val state = fixture.hass.states.getValue(entityId)
        assertEquals(expected["state"]?.stringOrNull, fixture.hass.formatEntityState(state))
    }

    @TestFactory
    fun `Given captured server data when showing default state content then it matches state-display`(): List<DynamicTest> = perEntity { fixture, entityId, expected ->
        val now = Instant.parse(fixture.json("outputs/entity-display.json").string("now"))
        val state = fixture.hass.states.getValue(entityId)
        assertEquals(expected["secondary"]?.stringOrNull, fixture.hass.stateDisplay(state, content = null, now = now))
    }

    @TestFactory
    fun `Given the tiles of the generated dashboard when deriving them then name and secondary line match hui-tile-card`(): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val display = fixture.json("outputs/entity-display.json")
        val now = Instant.parse(display.string("now"))
        display.objects("tiles").mapIndexed { index, captured ->
            val config = captured.obj("config")!!
            DynamicTest.dynamicTest("$variant $index ${config.string("entity")}") {
                val tile = fixture.hass.tileModel(CardConfig(config), now)
                assertEquals(captured.string("name") to captured.string("secondary"), tile?.name to tile?.state)
            }
        }
    }

    private fun perEntity(check: (GoldenFixture, String, JsonObject) -> Unit): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val entities = fixture.json("outputs/entity-display.json").obj("entities") ?: JsonObject(emptyMap())
        entities.map { (entityId, expected) ->
            DynamicTest.dynamicTest("$variant $entityId") { check(fixture, entityId, expected as JsonObject) }
        }
    }

    private companion object {
        fun item(type: String) = JsonObject(mapOf("type" to JsonPrimitive(type)))

        /** The `name` options capture.mjs passed to formatEntityName, by fixture key. */
        val NAME_CONFIGS: Map<String, JsonElement?> = mapOf(
            "default" to null,
            "entity" to item("entity"),
            "device" to item("device"),
            "area" to item("area"),
            "floor" to item("floor"),
            "device_entity" to JsonArray(listOf(item("device"), item("entity"))),
        )
    }
}
