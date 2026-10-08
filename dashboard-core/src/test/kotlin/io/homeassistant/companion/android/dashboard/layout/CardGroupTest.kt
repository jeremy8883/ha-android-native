package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.model.ViewConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardGroupTest {
    private fun view(json: String) = ViewConfig(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `Given sections view when grouped then each section is a group`() {
        val groups = cardGroups(view("""{"sections": [{"cards": [{"type": "tile"}, {"type": "heading"}]}, {"cards": []}]}"""))
        assertEquals(listOf(listOf("tile", "heading"), emptyList()), groups.map { group -> group.cards.map { it.type } })
    }

    @Test
    fun `Given masonry view when grouped then all cards form one group`() {
        val groups = cardGroups(view("""{"cards": [{"type": "entities"}, {"type": "glance"}]}"""))
        assertEquals(listOf(listOf("entities", "glance")), groups.map { group -> group.cards.map { it.type } })
    }
}
