package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The entities card's special rows (src/panels/lovelace/special-rows/) for the test instance's demo entities. */
class EntitiesSpecialRowsTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun rows(entities: String): List<EntitiesRow> = hass.entitiesModel(CardConfig(json("""{"type": "entities", "entities": [$entities]}""")), Instant.EPOCH).rows

    @Test
    fun `Given dividers and sections when deriving rows then they keep their place between entities`() {
        val rows = rows(
            """"light.bed_light", {"type": "divider"}, {"type": "section", "label": "Misc"}, {"type": "section"}""",
        )
        assertTrue(rows[0] is EntitiesRow.Entity)
        assertEquals(EntitiesRow.Divider, rows[1])
        assertEquals(EntitiesRow.Section("Misc"), rows[2])
        assertEquals(EntitiesRow.Section(null), rows[3])
    }

    @Test
    fun `Given weblinks when deriving rows then URLs open in the browser and paths in the app`() {
        val (site, path) = rows(
            """{"type": "weblink", "url": "https://www.home-assistant.io", "name": "HA"},
               {"type": "weblink", "url": "/lovelace/1"}""",
        ).map { it as EntitiesRow.Weblink }
        assertEquals(EntitiesRow.Weblink("HA", "mdi:link", CardAction.OpenUrl("https://www.home-assistant.io")), site)
        // Named after the URL without a name
        assertEquals("/lovelace/1", path.name)
        assertEquals(CardAction.Navigate("/lovelace/1", replace = false), path.action)
    }

    @Test
    fun `Given button rows when deriving them then they toggle what toggles and run actions with their data`() {
        val (light, call, perform) = rows(
            """{"type": "button", "entity": "light.bed_light"},
               {"type": "call-service", "name": "Off", "action": "switch.turn_off", "data": {"entity_id": "switch.ac"}},
               {"type": "perform-action", "name": "On", "action": "switch.turn_on", "action_name": "Go"}""",
        ).map { it as EntitiesRow.Button }
        assertEquals("Bed Light", light.name)
        assertEquals("Run", light.label)
        assertEquals("toggle", light.actions.config.obj("tap_action")?.string("action"))
        assertTrue(light.actions.hold)
        val tap = call.actions.config.obj("tap_action")!!
        assertEquals("perform-action", tap.string("action"))
        assertEquals("switch.turn_off", tap.string("perform_action"))
        assertEquals("switch.ac", tap.obj("data")?.string("entity_id"))
        assertEquals("Go", perform.label)
    }

    @Test
    fun `Given a buttons row when deriving it then each entity is a chip that toggles only when it can`() {
        val row = rows("""{"type": "buttons", "entities": ["light.bed_light", "sun.sun"]}""").single()
        val (light, sun) = (row as EntitiesRow.Buttons).buttons
        assertEquals("toggle", light.actions.config.obj("tap_action")?.string("action"))
        assertEquals("more-info", sun.actions.config.obj("tap_action")?.string("action"))
    }

    @Test
    fun `Given conditional rows when deriving them then only those whose conditions are met show`() {
        val rows = rows(
            """{"type": "conditional", "conditions": [{"entity": "switch.garden_irrigation", "state": "on"}],
                "row": {"entity": "light.bed_light"}},
               {"type": "conditional", "conditions": [{"entity": "switch.garden_irrigation", "state": "off"}],
                "row": {"type": "text", "name": "Irrigation", "text": "Off"}},
               {"type": "conditional", "row": {"entity": "light.bed_light"}}""",
        )
        assertEquals(EntitiesRow.Text("Irrigation", "Off", null), rows[0])
        assertTrue(rows[1] is EntitiesRow.Invalid)
        assertEquals(2, rows.size)
    }

    @Test
    fun `Given attribute rows when deriving them then they show the attribute between prefix and suffix`() {
        val rows = rows(
            """{"type": "attribute", "entity": "climate.heatpump", "attribute": "current_temperature"},
               {"type": "attribute", "entity": "climate.heatpump", "attribute": "temperature", "prefix": "To ", "suffix": " deg"},
               {"type": "attribute", "entity": "climate.heatpump", "attribute": "nothing"},
               {"type": "attribute", "entity": "climate.heatpump"}""",
        )
        val (current, target, missing) = rows.take(3).map { (it as EntitiesRow.Entity).row }
        assertEquals("HeatPump", current.name)
        assertNull(current.control)
        assertTrue(current.state!!.startsWith("25"))
        assertEquals("To 20 deg", target.state)
        assertEquals("—", missing.state)
        assertTrue(rows[3] is EntitiesRow.Invalid)
    }

    @Test
    fun `Given text, cast and unknown rows when deriving them then they follow upstream`() {
        val rows = rows(
            """{"type": "text", "name": "Hi", "text": "There", "icon": "mdi:text"}, {"type": "text", "name": "Hi"},
               {"type": "cast"}, {"type": "cast", "hide_if_unavailable": true}, {"type": "no-such-row"}""",
        )
        assertEquals(EntitiesRow.Text("Hi", "There", "mdi:text"), rows[0])
        assertTrue(rows[1] is EntitiesRow.Invalid)
        assertEquals(EntitiesRow.Cast("Home Assistant Cast", "mdi:television", "Cast API unavailable"), rows[2])
        assertTrue(rows[3] is EntitiesRow.Invalid)
        assertEquals(4, rows.size)
    }

    @Test
    fun `Given a simple entity row when deriving it then it shows the state without its control`() {
        val row = (rows("""{"type": "simple-entity", "entity": "light.bed_light"}""").single() as EntitiesRow.Entity).row
        assertNull(row.control)
        assertEquals("On", row.state)
    }
}
