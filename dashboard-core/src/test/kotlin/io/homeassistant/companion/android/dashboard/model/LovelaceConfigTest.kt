package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LovelaceConfigTest {
    private fun dashboard(json: String) = DashboardConfig(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `Given view without type when resolving view type then upstream defaults apply`() {
        val config = dashboard(
            """
            {"views": [
              {"type": "sidebar", "cards": []},
              {"panel": true, "cards": []},
              {"sections": []},
              {"cards": []},
              {}
            ]}
            """,
        )
        assertEquals(
            listOf(ViewType.SIDEBAR, ViewType.PANEL, ViewType.SECTIONS, ViewType.MASONRY, ViewType.SECTIONS),
            config.views.map { it.viewType },
        )
    }

    @Test
    fun `Given strategy dashboard when inspected then it is detected`() {
        val config = dashboard("""{"strategy": {"type": "map"}}""")
        assertTrue(config.isStrategy)
        assertEquals(emptyList<ViewConfig>(), config.views)
    }

    @Test
    fun `Given unknown keys when wrapped then they are preserved verbatim`() {
        val raw = """{"views":[{"title":"Home","future_key":{"x":1},"cards":[{"type":"custom:fancy","whatever":[1,2]}]}]}"""
        val config = dashboard(raw)
        assertEquals(Json.parseToJsonElement(raw), config.json)
        val card = config.views.single().cards.single()
        assertTrue(card.isCustom)
        assertFalse(config.isStrategy)
    }

    @Test
    fun `Given malformed values when read then they are treated as absent`() {
        val config = dashboard("""{"title": 5, "views": [{"cards": "nope", "subview": "yes"}, "not-an-object"]}""")
        assertEquals(null, config.title)
        val view = config.views.single()
        assertEquals(emptyList<CardConfig>(), view.cards)
        assertFalse(view.subview)
    }
}
