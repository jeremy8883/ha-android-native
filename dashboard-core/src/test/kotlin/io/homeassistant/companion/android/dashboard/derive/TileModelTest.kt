package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileModelTest {
    private val hass = hass(
        states(
            """
            {
              "sensor.temp": {"s": "21.5", "a": {"friendly_name": "Temperature", "unit_of_measurement": "°C"}, "c": "x", "lc": 1},
              "light.desk": {"s": "unavailable", "a": {"friendly_name": "Desk", "icon": "mdi:lamp"}, "c": "x", "lc": 1}
            }
            """,
        ),
    )

    private fun tile(config: String) = hass.tileModel(CardConfig(json(config)), NOW)

    @Test
    fun `Given tile without options when derived then friendly name, formatted state and domain icon are used`() {
        assertEquals(
            TileModel("sensor.temp", "Temperature", "21.5 °C", "mdi:eye", active = true, available = true),
            tile("""{"type": "tile", "entity": "sensor.temp"}"""),
        )
    }

    @Test
    fun `Given tile with name and icon override when derived then overrides win`() {
        val tile = tile("""{"type": "tile", "entity": "light.desk", "name": "Lamp", "icon": "mdi:desk"}""")
        assertEquals("Lamp", tile?.name)
        assertEquals("mdi:desk", tile?.icon)
        assertEquals(false, tile?.available)
        assertEquals(false, tile?.active)
    }

    @Test
    fun `Given tile with state content when derived then the secondary line follows it`() {
        val tile = tile("""{"type": "tile", "entity": "light.desk", "state_content": ["name", "state"]}""")
        assertEquals("[state.default.unavailable]", tile?.state)
    }

    @Test
    fun `Given tile with hide_state when derived then the state is hidden`() {
        assertNull(tile("""{"type": "tile", "entity": "sensor.temp", "hide_state": true}""")?.state)
    }

    @Test
    fun `Given tile for missing entity when derived then it is null`() {
        assertNull(tile("""{"type": "tile", "entity": "light.gone"}"""))
        assertNull(tile("""{"type": "tile"}"""))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-08T12:00:00Z")
    }
}
