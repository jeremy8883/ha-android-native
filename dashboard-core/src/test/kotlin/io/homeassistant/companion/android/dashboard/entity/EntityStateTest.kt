package io.homeassistant.companion.android.dashboard.entity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class EntityStateTest {
    private fun event(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val initial = applyEntityEvent(
        emptyMap(),
        event(
            """
            {"a": {
              "light.kitchen": {"s": "on", "a": {"brightness": 128, "friendly_name": "Kitchen"}, "c": "ctx1", "lc": 100.5},
              "sensor.temp": {"s": "21.5", "a": {"unit_of_measurement": "°C"}, "c": {"id": "ctx2", "parent_id": null, "user_id": "u1"}, "lc": 50.0, "lu": 60.0}
            }}
            """,
        ),
    )

    @Test
    fun `Given add event when applied then states are decoded and lu defaults to lc`() {
        val light = initial.getValue("light.kitchen")
        assertEquals("on", light.state)
        assertEquals(JsonPrimitive(128), light.attributes["brightness"])
        assertEquals("ctx1", light.contextId)
        assertEquals(100.5, light.lastChanged)
        assertEquals(100.5, light.lastUpdated)
        assertEquals("light", light.domain)

        val sensor = initial.getValue("sensor.temp")
        assertEquals("ctx2", sensor.contextId)
        assertEquals(60.0, sensor.lastUpdated)
    }

    @Test
    fun `Given change with lc when applied then state and both timestamps update`() {
        val updated = applyEntityEvent(initial, event("""{"c": {"light.kitchen": {"+": {"s": "off", "lc": 200.0, "c": "ctx3"}}}}"""))
        val light = updated.getValue("light.kitchen")
        assertEquals("off", light.state)
        assertEquals(200.0, light.lastChanged)
        assertEquals(200.0, light.lastUpdated)
        assertEquals("ctx3", light.contextId)
        assertEquals(JsonPrimitive(128), light.attributes["brightness"])
    }

    @Test
    fun `Given attribute-only change when applied then only lu and attributes change`() {
        val updated = applyEntityEvent(
            initial,
            event("""{"c": {"light.kitchen": {"+": {"a": {"brightness": 255}, "lu": 150.0}, "-": {"a": ["friendly_name"]}}}}"""),
        )
        val light = updated.getValue("light.kitchen")
        assertEquals("on", light.state)
        assertEquals(100.5, light.lastChanged)
        assertEquals(150.0, light.lastUpdated)
        assertEquals(JsonPrimitive(255), light.attributes["brightness"])
        assertNull(light.attributes["friendly_name"])
    }

    @Test
    fun `Given remove event when applied then entity is removed`() {
        val updated = applyEntityEvent(initial, event("""{"r": ["sensor.temp"]}"""))
        assertEquals(setOf("light.kitchen"), updated.keys)
    }

    @Test
    fun `Given diff for unknown entity when applied then it is ignored`() {
        val updated = applyEntityEvent(initial, event("""{"c": {"switch.nope": {"+": {"s": "on"}}}}"""))
        assertEquals(initial, updated)
    }

    @Test
    fun `Given unchanged entity when another changes then its instance is reused`() {
        val updated = applyEntityEvent(initial, event("""{"c": {"light.kitchen": {"+": {"s": "off"}}}}"""))
        assertSame(initial.getValue("sensor.temp"), updated.getValue("sensor.temp"))
    }

    @Test
    fun `Given states when written compressed and read back then they are the same`() {
        val compressed = JsonObject(initial.mapValues { it.value.toCompressed() })
        assertEquals(initial, applyEntityEvent(emptyMap(), JsonObject(mapOf("a" to compressed))))
    }
}
