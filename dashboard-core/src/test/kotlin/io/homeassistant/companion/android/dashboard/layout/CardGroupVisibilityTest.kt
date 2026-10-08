package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CardGroupVisibilityTest {
    private val hass = hass(states("""{"light.a": {"s": "on", "a": {}}}"""))
    private val phone = ConditionContext(maxColumns = 1)

    private fun card(json: String) = CardConfig(json(json))
    private val largeOnly = card("""{"type": "heading", "visibility": [{"condition": "view_columns", "min": 2}]}""")
    private val tile = card("""{"type": "tile", "entity": "light.a"}""")
    private val tileWhenOff = card("""{"type": "tile", "entity": "light.a", "visibility": [{"condition": "state", "state": "off"}]}""")

    @Test
    fun `Given cards with visibility when on a phone then hidden cards are dropped and the card entity is the context`() {
        assertEquals(listOf(tile), hass.visibleCards(CardGroup(listOf(largeOnly, tile, tileWhenOff)), phone))
    }

    @Test
    fun `Given section that is disabled, fails its visibility, or has only hidden cards then it is hidden`() {
        val visibility = json("""{"v": [{"condition": "view_columns", "max": 1}]}""").objects("v")
        assertNull(hass.visibleCards(CardGroup(listOf(tile), disabled = true), phone))
        assertNull(hass.visibleCards(CardGroup(listOf(tile), visibility = visibility), ConditionContext(maxColumns = 3)))
        assertNull(hass.visibleCards(CardGroup(listOf(largeOnly, tileWhenOff)), phone))
        assertEquals(emptyList<CardConfig>(), hass.visibleCards(CardGroup(emptyList()), phone))
    }
}
