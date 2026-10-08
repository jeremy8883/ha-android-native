package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.model.CardConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileModelTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private val states = applyEntityEvent(
        emptyMap(),
        json(
            """
            {"a": {
              "sensor.temp": {"s": "21.5", "a": {"friendly_name": "Temperature", "unit_of_measurement": "°C"}, "c": "x", "lc": 1},
              "light.desk": {"s": "unavailable", "a": {"friendly_name": "Desk", "icon": "mdi:lamp"}, "c": "x", "lc": 1}
            }}
            """,
        ),
    )

    @Test
    fun `Given tile without name when derived then friendly name and unit are used`() {
        val tile = tileModel(CardConfig(json("""{"type": "tile", "entity": "sensor.temp"}""")), states)
        assertEquals(TileModel("sensor.temp", "Temperature", "21.5", "°C", null, active = true, available = true), tile)
    }

    @Test
    fun `Given tile with name and icon override when derived then overrides win`() {
        val tile = tileModel(CardConfig(json("""{"type": "tile", "entity": "light.desk", "name": "Lamp", "icon": "mdi:desk"}""")), states)
        assertEquals("Lamp", tile?.name)
        assertEquals("mdi:desk", tile?.icon)
        assertEquals(false, tile?.available)
        assertEquals(false, tile?.active)
    }

    @Test
    fun `Given tile for missing entity when derived then it is null`() {
        assertNull(tileModel(CardConfig(json("""{"type": "tile", "entity": "light.gone"}""")), states))
        assertNull(tileModel(CardConfig(json("""{"type": "tile"}""")), states))
    }
}
