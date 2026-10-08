package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Media control, entities and empty state models for the test instance's demo entities. */
class CardModelsTest {
    private val hass = GoldenFixture("test-instance").hass

    @Test
    fun `Given a playing music speaker when deriving its media card then title, artist and controls follow upstream`() {
        val media = hass.mediaControlModel(CardConfig(json("""{"type": "media-control", "entity": "media_player.kitchen_speaker"}""")))!!
        assertEquals("I Wanna Be A Hippy (Flamman & Abraxas Radio Mix)", media.title)
        assertEquals("Technohead", media.subtitle)
        assertEquals(
            listOf("turn_off", "media_previous_track", "media_pause", "media_next_track"),
            media.controls.map { it.action.service },
        )
        assertTrue(media.picture!!.startsWith("/api/media_player_proxy/"))
        assertFalse(media.off)
    }

    @Test
    fun `Given a playing video player without track controls when deriving its media card then app name, power and pause show`() {
        val media = hass.mediaControlModel(CardConfig(json("""{"type": "media-control", "entity": "media_player.living_room_tv"}""")))!!
        assertEquals("YouTube", media.subtitle)
        assertEquals(listOf("turn_off", "media_pause"), media.controls.map { it.action.service })
    }

    @Test
    fun `Given an entities card when deriving rows then controls follow the row type of each domain`() {
        val model = hass.entitiesModel(
            CardConfig(
                json(
                    """{"type": "entities", "title": "Device", "entities": [
                      "light.kitchen_lights", {"entity": "lock.front_door_deadbolt", "name": {"type": "entity"}},
                      "button.push", "sensor.outside_temperature", "light.missing", "datetime.date_and_time"]}""",
                ),
            ),
            Instant.EPOCH,
        )
        assertEquals("Device", model.title)
        val (light, lock, button, sensor, missing) = model.rows
        assertEquals(true, (light.control as RowControl.Toggle).checked)
        assertEquals("Unlock", (lock.control as RowControl.Buttons).buttons.single().label)
        assertEquals("press", (button.control as RowControl.Buttons).buttons.single().action.service)
        assertNull(sensor.control)
        assertEquals("15.6 °C", sensor.state)
        assertTrue(missing.missing)
        // Only sensor rows show relative times; others show the formatted state
        assertEquals("January 1, 2020 at 12:00 PM", model.rows.last().state)
        assertTrue(light.actions.tap)
    }

    @Test
    fun `Given an empty state card when deriving it then icon colour and button actions are kept`() {
        val model = emptyStateModel(
            CardConfig(
                json(
                    """{"type": "empty-state", "icon": "mdi:check-all", "icon_color": "primary", "content_only": true,
                    "title": "All organized", "buttons": [{"text": "Add", "tap_action": {"action": "navigate", "navigation_path": "x"}}]}""",
                ),
            ),
        )
        assertEquals(DisplayColor.Theme("primary"), model.iconColor)
        assertTrue(model.contentOnly)
        assertTrue(model.buttons.single().actions.tap)
    }
}

class ShortcutModelTest {
    private val hass = GoldenFixture("test-instance").hass

    @Test
    fun `Given shortcuts without label or icon when deriving them then the target supplies them`() {
        fun shortcut(action: String) = hass.shortcutModel(CardConfig(json("""{"type": "shortcut", "tap_action": $action}""")))
        val area = shortcut("""{"action": "navigate", "navigation_path": "areas-kitchen"}""")
        assertEquals("Kitchen" to "mdi:stove", area.label to area.icon)
        val url = shortcut("""{"action": "url", "url_path": "https://example.com"}""")
        assertEquals("https://example.com" to "mdi:open-in-new", url.label to url.icon)
        val explicit = hass.shortcutModel(
            CardConfig(json("""{"type": "shortcut", "label": "Garden", "icon": "mdi:flower", "color": "green"}""")),
        )
        assertEquals(Triple("Garden", "mdi:flower", DisplayColor.Theme("green")), Triple(explicit.label, explicit.icon, explicit.color))
    }
}
