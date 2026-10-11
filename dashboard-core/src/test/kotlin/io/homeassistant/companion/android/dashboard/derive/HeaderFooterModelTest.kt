package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeaderFooterModelTest {
    private val hass = GoldenFixture("test-instance").hass

    @Test
    fun `Given a buttons footer when deriving it then each entity toggles on tap and shows its name only when asked`() {
        val model = hass.headerFooterModel(
            json(
                """{"type": "buttons", "entities": ["light.kitchen_lights",
                {"entity": "light.bed_light", "name": "Bed"}, {"entity": "scene.movie_time", "show_name": true}]}""",
            ),
        ) as HeaderFooterModel.Buttons
        val (kitchen, bed, scene) = model.buttons
        assertEquals("" to "toggle", kitchen.name to kitchen.actions.config.obj("tap_action")?.string("action"))
        assertEquals("more-info", kitchen.actions.config.obj("hold_action")?.string("action"))
        assertEquals("Bed", bed.name)
        assertEquals("call-service", scene.actions.config.obj("tap_action")?.string("action"))
        assertTrue(kitchen.badge != null)
    }

    @Test
    fun `Given a graph header when deriving it then it shows the sensor's last day`() {
        val model = hass.headerFooterModel(json("""{"type": "graph", "entity": "sensor.outside_temperature"}"""))
        val graph = (model as HeaderFooterModel.Graph).graph
        assertEquals(24.0 to 1, graph.hoursToShow to graph.detail)
    }

    @Test
    fun `Given invalid elements when deriving them then a configuration error shows`() {
        val error = HeaderFooterModel.Invalid(hass.localize("ui.errors.config.configuration_error"))
        assertEquals(error, hass.headerFooterModel(json("""{"type": "graph", "entity": "light.kitchen_lights"}""")))
        assertEquals(error, hass.headerFooterModel(json("""{"type": "picture"}""")))
        assertEquals(error, hass.headerFooterModel(json("""{"type": "odd"}""")))
        assertNull(hass.headerFooterModel(null))
    }

    @Test
    fun `Given an entities card with a graph footer when listing its graphs then the footer's is loaded`() {
        val card = CardConfig(
            json(
                """{"type": "entities", "entities": [], "footer": {"type": "graph", "entity": "sensor.outside_temperature",
                "hours_to_show": 12}}""",
            ),
        )
        assertEquals(listOf(12.0), headerFooterGraphs(card).map { it.hoursToShow })
    }
}
