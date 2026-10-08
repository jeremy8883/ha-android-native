package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ViewLayoutTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            """{"type": "tile"}                                                  | 6  | 1""",
            """{"type": "tile", "features": [{}, {}]}                            | 6  | 3""",
            """{"type": "tile", "features": [{}], "features_position": "inline"} | 12 | 1""",
            """{"type": "tile", "vertical": true}                                | 6  | 2""",
            """{"type": "heading"}                                               |    | """,
            """{"type": "area", "display_type": "compact"}                       | 6  | 1""",
            """{"type": "area"}                                                  | 6  | 3""",
            """{"type": "button"}                                                | 6  | 2""",
            """{"type": "button", "show_name": false}                            | 3  | 1""",
            """{"type": "markdown"}                                              | 12 | """,
            """{"type": "tile", "grid_options": {"columns": 3, "rows": 2}}       | 6  | 2""",
            """{"type": "tile", "grid_options": {"columns": "full"}}             |    | 1""",
            """{"type": "heading", "grid_options": {"columns": 4, "rows": 2}}    | 4  | 2""",
            """{"type": "markdown", "layout_options": {"grid_columns": 2}}       | 6  | """,
        ],
    )
    fun `Given a card config when sizing it then it follows the card's grid options and overrides`(
        config: String,
        columns: Int?,
        rows: Int?,
    ) {
        assertEquals(CardGridSize(columns, rows), cardGridSize(CardConfig(json(config))))
    }

    @Test
    fun `Given mixed spans when placing in a grid then items flow left to right and skip occupied cells`() {
        // A 2-row item, then four 1-row items: the row beside it is filled before wrapping
        val cells = placeInGrid(listOf(6 to 2, 6 to 1, 6 to 1, 12 to 1, 3 to 1), columnCount = 12)
        assertEquals(
            listOf(
                GridCell(0, 0, 2, 6),
                GridCell(0, 6, 1, 6),
                GridCell(1, 6, 1, 6),
                GridCell(2, 0, 1, 12),
                GridCell(3, 0, 1, 3),
            ),
            cells,
        )
    }

    @Test
    fun `Given an item that does not fit after the cursor when placing then it never goes back to an earlier gap`() {
        val cells = placeInGrid(listOf(3 to 1, 12 to 1, 3 to 1), columnCount = 12)
        assertEquals(listOf(GridCell(0, 0, 1, 3), GridCell(1, 0, 1, 12), GridCell(2, 0, 1, 3)), cells)
    }

    @Test
    fun `Given sections when laid out on a wide screen then columns are limited by the visible spans`() {
        val view = ViewConfig(
            json(
                """
                {"type": "sections", "sections": [
                  {"type": "grid", "column_span": 2, "cards": [{"type": "heading"}, {"type": "tile", "entity": "light.a"}]},
                  {"type": "grid", "cards": [{"type": "tile", "entity": "light.a"}],
                   "visibility": [{"condition": "state", "entity": "light.a", "state": "off"}]},
                  {"type": "grid", "column_span": 5, "cards": []}
                ]}
                """,
            ),
        )
        val hass = hass(states("""{"light.a": {"s": "on", "a": {}}}"""))
        val layout = hass.viewLayout(cardGroups(view), ConditionContext(maxColumns = 3))

        assertEquals(3, layout.columnCount)
        // The hidden section takes no space; the 5-column section is narrowed to the 3 columns
        assertEquals(listOf(GridCell(0, 0, 1, 2), GridCell(1, 0, 1, 3)), layout.sections.map { it.cell })
        assertEquals(24, layout.sections[0].columnCount)
        assertEquals(
            listOf(GridCell(0, 0, 1, 24) to null, GridCell(1, 0, 1, 6) to 1),
            layout.sections[0].cards.map { it.cell to it.fixedRows },
        )
    }

    @Test
    fun `Given a phone when laying out sections then each takes the single column`() {
        val view = ViewConfig(
            json("""{"type": "sections", "sections": [{"column_span": 2, "cards": []}, {"cards": []}]}"""),
        )
        val layout = hass(states("{}")).viewLayout(cardGroups(view), ConditionContext(maxColumns = 1))
        assertEquals(1, layout.columnCount)
        assertEquals(listOf(GridCell(0, 0, 1, 1), GridCell(1, 0, 1, 1)), layout.sections.map { it.cell })
        assertEquals(12, layout.sections[0].columnCount)
    }

    @Test
    fun `Given a masonry view when laid out then its cards are stacked in one column`() {
        val view = ViewConfig(json("""{"cards": [{"type": "markdown"}, {"type": "tile", "entity": "light.a"}]}"""))
        val layout = hass(states("{}")).viewLayout(cardGroups(view), ConditionContext(maxColumns = 3))
        assertEquals(listOf(GridCell(0, 0, 1, 1), GridCell(1, 0, 1, 1)), layout.sections.single().cards.map { it.cell })
    }
}
