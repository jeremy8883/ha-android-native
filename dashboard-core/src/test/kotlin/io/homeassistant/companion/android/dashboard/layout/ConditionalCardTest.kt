package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionalCardTest {
    private val card = CardConfig(
        json(
            """{"type": "conditional", "conditions": [{"condition": "state", "entity": "light.a", "state": "on"}],
                "card": {"type": "tile", "entity": "light.a"}}""",
        ),
    )
    private val context = ConditionContext(maxColumns = 1)

    @Test
    fun `Given a conditional card when its conditions change then it is laid out only while they pass`() {
        assertTrue(hass(states("""{"light.a": {"s": "on", "a": {}}}""")).conditionalCardShown(card, context))
        assertFalse(hass(states("""{"light.a": {"s": "off", "a": {}}}""")).conditionalCardShown(card, context))
    }

    @Test
    fun `Given a conditional card when looking inside then its card is found for rendering and data`() {
        assertEquals("tile", card.conditionalInnerCard()?.type)
        assertEquals(listOf("conditional", "tile"), withNestedCards(listOf(card)).map { it.type })
    }
}
