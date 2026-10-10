package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StackCardTest {
    private fun card(text: String) = CardConfig(json(text))

    @Test
    fun `Given stack configs when reading them then they arrange their cards as upstream's`() {
        val vertical = card("""{"type": "vertical-stack", "title": "Hall", "cards": [{"type": "tile", "entity": "light.a"}]}""")
        assertEquals(StackCard("Hall", StackArrangement.Vertical, listOf(card("""{"type": "tile", "entity": "light.a"}"""))), vertical.stackCard())
        assertEquals(StackArrangement.Horizontal, card("""{"type": "horizontal-stack", "cards": []}""").stackCard()?.arrangement)
        assertEquals(StackArrangement.Grid(columns = 3, square = true), card("""{"type": "grid", "cards": []}""").stackCard()?.arrangement)
        assertEquals(
            StackArrangement.Grid(columns = 2, square = false),
            card("""{"type": "grid", "columns": 2, "square": false, "cards": []}""").stackCard()?.arrangement,
        )
        // Upstream rejects a stack without a list of cards
        assertNull(card("""{"type": "vertical-stack"}""").stackCard())
        assertNull(card("""{"type": "tile", "entity": "light.a"}""").stackCard())
    }

    @Test
    fun `Given a stack with hidden cards when showing it then only the shown cards are laid out`() {
        val stack = card(
            """{"type": "vertical-stack", "cards": [
                {"type": "tile", "entity": "light.a"},
                {"type": "tile", "entity": "light.b", "visibility": [{"condition": "state", "entity": "light.a", "state": "on"}]},
                {"type": "conditional", "conditions": [{"condition": "state", "entity": "light.a", "state": "off"}], "card": {"type": "tile", "entity": "light.c"}}
            ]}""",
        ).stackCard()!!
        val on = hass(states("""{"light.a": {"s": "on", "a": {}}}"""))
        assertEquals(listOf("light.a", "light.b"), on.visibleStackCards(stack, ConditionContext(maxColumns = 1)).map { it.entity })
        val off = hass(states("""{"light.a": {"s": "off", "a": {}}}"""))
        assertEquals(listOf("light.a", null), off.visibleStackCards(stack, ConditionContext(maxColumns = 1)).map { it.entity })
    }

    @Test
    fun `Given nested stacks when collecting the cards for their data then every inner card is found`() {
        val stack = card(
            """{"type": "vertical-stack", "cards": [{"type": "horizontal-stack", "cards": [{"type": "markdown"}]}]}""",
        )
        assertEquals(listOf("vertical-stack", "horizontal-stack", "markdown"), withNestedCards(listOf(stack)).map { it.type })
    }
}
