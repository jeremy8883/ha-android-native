package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeadingModelTest {
    private val hass = hass(
        states(
            """
            {
              "light.a": {"s": "on", "a": {"friendly_name": "A", "rgb_color": [255, 255, 255]}},
              "light.b": {"s": "off", "a": {"friendly_name": "B"}},
              "sensor.battery": {"s": "15", "a": {"device_class": "battery", "unit_of_measurement": "%"}},
              "sensor.gone": {"s": "unavailable", "a": {}}
            }
            """,
        ),
    )

    private fun heading(config: String) = hass.headingModel(CardConfig(json(config)), ConditionContext(), NOW)

    @Test
    fun `Given the area lights heading when one light is on then only the turn off button shows`() {
        val anyOn = """{"condition": "or", "conditions": [{"condition": "state", "entity": "light.a", "state": "on"}]}"""
        val model = heading(
            """
            {"type": "heading", "heading": "Lights", "icon": "mdi:lamps",
             "tap_action": {"action": "navigate", "navigation_path": "/light"},
             "badges": [
               {"type": "button", "icon": "mdi:power", "text": "Off", "visibility": [{"condition": "not", "conditions": [$anyOn]}],
                "tap_action": {"action": "perform-action", "perform_action": "light.turn_on"}},
               {"type": "button", "icon": "mdi:power", "text": "On", "color": "orange", "visibility": [$anyOn],
                "tap_action": {"action": "perform-action", "perform_action": "light.turn_off"}}
             ]}
            """,
        )
        assertTrue(model.actionable)
        val badge = model.badges.single() as HeadingBadgeModel.Button
        assertEquals("On", badge.text)
        assertEquals(DisplayColor.Theme("orange"), badge.color)
        assertTrue(badge.actions.tap)
    }

    @Test
    fun `Given entity badges when deriving then state, colours and missing entities follow upstream`() {
        val model = heading(
            """
            {"type": "heading", "heading": "Device", "badges": [
              {"entity": "sensor.battery", "color": "state"},
              {"entity": "light.a", "color": "state"},
              {"entity": "light.b", "color": "red"},
              {"entity": "sensor.gone"},
              {"entity": "sensor.missing"},
              {"type": "entity", "entity": "light.a", "show_state": false, "show_icon": false}
            ]}
            """,
        )
        val badges = model.badges.map { it as HeadingBadgeModel.Entity }
        assertEquals("15%", badges[0].state)
        assertEquals(DisplayColor.State(listOf("state-sensor-battery-low-color")), badges[0].color)
        // A white light gets a fixed light grey for contrast
        assertEquals(DisplayColor.Literal("#e1e1e1"), badges[1].color)
        assertEquals(null, badges[2].color)
        assertEquals("—", badges[3].state)
        assertTrue(badges[4].missing)
        assertEquals(null to null, badges[5].icon to badges[5].state)
        assertFalse(badges[0].actions.interactive)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-08T12:00:00Z")
    }
}
