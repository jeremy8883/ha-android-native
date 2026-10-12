package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AreaCardModelTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun area(config: String) = hass.areaCardModel(CardConfig(json(config))) as AreaCardModel.Shown

    @Test
    fun `Given the home overview's compact area card when deriving it then it is a tile with the area's temperature`() {
        val model = area(
            """{"type": "area", "area": "living_room", "display_type": "compact", "sensor_classes": ["temperature"],
            "vertical": true, "tap_action": {"action": "navigate", "navigation_path": "areas-living_room"}}""",
        )
        assertNull(model.header)
        assertTrue(model.vertical)
        val temperature = hass.states.getValue("sensor.living_room_temperature")
        assertEquals(hass.formatEntityState(temperature), model.sensors)
        assertEquals("mdi:sofa" to "Living Room", model.icon to model.name)
        assertTrue(model.actions.tap)
    }

    @Test
    fun `Given a picture area card without a picture when deriving it then its header is the icon at 16 by 9`() {
        val model = area("""{"type": "area", "area": "kitchen"}""")
        assertEquals(AreaHeader.Icon("mdi:stove", 16.0 / 9.0), model.header)
        // No tap action by default, so neither the tile nor the picture reacts
        assertTrue(!model.actions.tap && model.imageActions == null)
    }

    @Test
    fun `Given a camera area card with a live view when deriving it then its header streams the area's camera`() {
        val camera = EntityEntry(
            "camera.demo_camera", null, "kitchen", emptyList(), null, "demo", null, false, null, null, false, null,
        )
        val withCamera = hass.copy(
            registries = hass.registries.copy(entities = hass.registries.entities + (camera.entityId to camera)),
        )
        fun header(view: String) = (
            withCamera.areaCardModel(
                CardConfig(json("""{"type": "area", "area": "kitchen", "display_type": "camera"$view}""")),
            ) as AreaCardModel.Shown
            ).header
        assertEquals(AreaHeader.LiveCamera("camera.demo_camera", 16.0 / 9.0), header(""", "camera_view": "live""""))
        assertTrue(header("") is AreaHeader.Picture)
        val live = CardConfig(json("""{"type": "area", "area": "kitchen", "display_type": "camera", "camera_view": "live"}"""))
        assertTrue(withCamera.areaCameraEntities(listOf(live)).isEmpty())
    }

    @Test
    fun `Given a legacy navigation path when deriving an area card then the tile and picture navigate`() {
        val model = area("""{"type": "area", "area": "kitchen", "navigation_path": "kitchen"}""")
        assertEquals("navigate", model.actions.config.obj("tap_action")?.string("action"))
        assertTrue(model.imageActions?.tap == true)
    }

    @Test
    fun `Given an unknown area when deriving an area card then upstream's warning shows`() {
        val model = hass.areaCardModel(CardConfig(json("""{"type": "area", "area": "nowhere"}""")))
        assertEquals(AreaCardModel.Warning(hass.localize("ui.card.area.area_not_found")), model)
    }

    @Test
    fun `Given area controls when deriving an area card then the area's kinds of devices get a button`() {
        val model = area("""{"type": "area", "area": "living_room", "features": [{"type": "area-controls"}]}""")
        val buttons = model.features.single().buttons
        assertTrue(buttons.isNotEmpty() && buttons.size <= 4)
        assertTrue(buttons.all { it.action.data?.get("entity_id") != null })
    }

    @Test
    fun `Given grouped states when computing a group state then covers report their most moving state`() {
        fun state(id: String, value: String) = EntityState(id, value, JsonObject(emptyMap()), null, 0.0, 0.0)
        assertEquals("opening", groupEntitiesState(listOf(state("cover.a", "closed"), state("cover.b", "opening"))))
        assertEquals("on", groupEntitiesState(listOf(state("light.a", "off"), state("light.b", "on"))))
        assertEquals("unknown", groupEntitiesState(listOf(state("light.a", "unknown"), state("light.b", "unavailable"))))
        assertEquals("unavailable", groupEntitiesState(emptyList()))
    }
}
