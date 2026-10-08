package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.TemplateResult
import io.homeassistant.companion.android.dashboard.derive.cardHidesItself
import io.homeassistant.companion.android.dashboard.derive.markdownModel
import io.homeassistant.companion.android.dashboard.derive.markdownTemplate
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.states
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewHeaderTest {
    private val hass = hass(
        states(
            """
            {
              "sensor.temp": {"s": "21.5", "a": {"unit_of_measurement": "°C", "device_class": "temperature", "friendly_name": "Temp"}},
              "light.desk": {"s": "off", "a": {"friendly_name": "Desk"}}
            }
            """,
        ),
    )

    @Test
    fun `Given a markdown card when deriving its template then config and user are passed as variables`() {
        val card = CardConfig(json("""{"type": "markdown", "content": "## Welcome {{ user }}", "text_only": true}"""))
        val request = hass.markdownTemplate(card)!!
        assertEquals("## Welcome {{ user }}", request.template)
        assertEquals(JsonPrimitive("Dev"), request.variables["user"])
        assertEquals(card.json, request.variables["config"])

        assertNull(hass.markdownModel(card)?.content)
        val rendered = hass.copy(templates = mapOf(request to TemplateResult.Rendered("## Welcome Dev")))
        assertEquals("## Welcome Dev", rendered.markdownModel(card)?.content)
        assertNull(rendered.markdownModel(card)?.title)
    }

    @Test
    fun `Given a markdown card with show_empty false when the template renders nothing then it hides itself`() {
        val card = CardConfig(json("""{"type": "markdown", "content": "{{ '' }}", "show_empty": false}"""))
        val request = hass.markdownTemplate(card)!!
        assertFalse(hass.cardHidesItself(card))
        assertTrue(hass.copy(templates = mapOf(request to TemplateResult.Rendered(""))).cardHidesItself(card))
    }

    @Test
    fun `Given a view with a header card and badges when deriving the header then badges follow upstream defaults`() {
        val view = ViewConfig(
            json(
                """
                {"type": "sections", "header": {"card": {"type": "markdown", "content": "Hi"}, "badges_position": "bottom"},
                 "badges": [
                   {"type": "entity", "entity": "sensor.temp", "color": "red"},
                   {"entity": "light.desk", "color": "indigo", "show_name": true},
                   "light.desk",
                   {"type": "entity", "entity": "sensor.temp", "visibility": [{"condition": "state", "entity": "light.desk", "state": "on"}]}
                 ]}
                """,
            ),
        )
        val header = hass.viewHeader(view, ConditionContext(), Instant.EPOCH)!!
        assertEquals("markdown", header.card?.type)
        assertEquals(3, header.badges.size)
        val (temp, desk, legacy) = header.badges
        assertEquals("21.5 °C", temp.content)
        assertEquals(DisplayColor.Theme("red"), temp.color)
        assertTrue(temp.actions.tap)
        // A custom colour only applies while active; with name and state, the name becomes the label
        assertNull(desk.color)
        assertEquals("Desk", desk.label)
        assertEquals("light.desk", legacy.entityId)
    }
}
