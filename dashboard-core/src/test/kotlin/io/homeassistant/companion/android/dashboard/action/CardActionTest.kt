package io.homeassistant.companion.android.dashboard.action

import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CardActionTest {
    private val hass = hass(
        states(
            """
            {
              "light.desk": {"s": "off", "a": {}}, "lock.door": {"s": "locked", "a": {}}, "cover.blind": {"s": "open", "a": {}},
              "group.all": {"s": "on", "a": {}}, "scene.movie": {"s": "unknown", "a": {}}, "sensor.temp": {"s": "21", "a": {}}
            }
            """,
        ),
    ).copy(localize = checkNotNull(JsonTranslations.bundled()))

    @ParameterizedTest
    @CsvSource(
        "light.desk, light, turn_on",
        "lock.door, lock, unlock",
        "cover.blind, cover, close_cover",
        "group.all, homeassistant, turn_off",
        "scene.movie, scene, turn_on",
    )
    fun `Given an entity when toggling then the domain's service for its state is called`(entity: String, domain: String, service: String) {
        val call = hass.toggleEntity(entity)
        assertEquals(domain to service, call.domain to call.service)
        assertEquals(buildJsonObject { put("entity_id", entity) }, call.data)
    }

    @Test
    fun `Given a tile without actions when tapping card and icon then it opens more-info and toggles`() {
        val actions = cardActions(CardConfig(json("""{"type": "tile", "entity": "light.desk"}""")))
        assertTrue(actions.card.tap)
        assertFalse(actions.card.hold)
        assertEquals(CardAction.MoreInfo("light.desk"), hass.resolveAction(actions.card.config, Gesture.TAP)?.action)
        assertEquals(hass.toggleEntity("light.desk"), hass.resolveAction(actions.icon!!.config, Gesture.TAP)?.action)
    }

    @Test
    fun `Given a sensor tile when looking at its icon then it is not interactive`() {
        val actions = cardActions(CardConfig(json("""{"type": "tile", "entity": "sensor.temp"}""")))
        assertFalse(actions.icon!!.interactive)
        assertNull(hass.resolveAction(actions.icon!!.config, Gesture.TAP))
    }

    @Test
    fun `Given a perform-action with confirmation when resolving then the service and default confirmation are returned`() {
        val config = json(
            """{"tap_action": {"action": "perform-action", "perform_action": "light.turn_on",
                "target": {"area_id": "kitchen"}, "data": {"brightness": 10}, "confirmation": true}}""",
        )
        val resolved = hass.resolveAction(config, Gesture.TAP)
        assertEquals(
            CardAction.CallService("light", "turn_on", json("""{"brightness": 10}"""), json("""{"area_id": "kitchen"}""")),
            resolved?.action,
        )
        assertEquals("Are you sure you want to run action 'Perform action'?", resolved?.confirmation?.text)
    }

    @Test
    fun `Given a confirmation the user is exempt from when resolving then no confirmation is asked`() {
        val config = json(
            """{"tap_action": {"action": "toggle", "confirmation": {"text": "Sure?", "exemptions": [{"user": "u1"}]}},
                "entity": "lock.door"}""",
        )
        assertNull(hass.resolveAction(config, Gesture.TAP)?.confirmation)
    }

    @Test
    fun `Given incomplete actions when resolving then they fail with upstream's messages`() {
        assertEquals(
            CardAction.Failure("No navigation path specified"),
            hass.resolveAction(json("""{"tap_action": {"action": "navigate"}}"""), Gesture.TAP)?.action,
        )
        assertEquals(
            CardAction.Failure("No entity provided for more info dialog"),
            hass.resolveAction(json("{}"), Gesture.HOLD)?.action,
        )
    }

    @Test
    fun `Given a heading with a navigate tap when looking at its gestures then only tap is interactive`() {
        val actions = cardActions(
            CardConfig(json("""{"type": "heading", "tap_action": {"action": "navigate", "navigation_path": "lights"}}""")),
        )
        assertTrue(actions.card.tap)
        assertFalse(actions.card.doubleTap)
        assertEquals(CardAction.Navigate("lights", replace = false), hass.resolveAction(actions.card.config, Gesture.TAP)?.action)
        assertFalse(cardActions(CardConfig(json("""{"type": "markdown"}"""))).card.interactive)
    }
}
