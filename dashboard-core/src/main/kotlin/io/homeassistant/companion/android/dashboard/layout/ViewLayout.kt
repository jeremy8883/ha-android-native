package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.conditionsMet
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A card and its place in its section's grid.
 *
 * @property fixedRows rows of [SECTION_ROW_HEIGHT_DP] the card is sized to, `null` when sized by its content
 */
data class PlacedCard(val card: CardConfig, val cell: GridCell, val fixedRows: Int?)

/**
 * A visible section: its place in the view's grid, and its cards in a grid of [columnCount] columns
 * ([SECTION_BASE_COLUMNS] per view column it spans; 1 for stacked cards).
 */
data class SectionLayout(val cell: GridCell, val columnCount: Int, val cards: List<PlacedCard>)

/**
 * The visible sections of a view laid out in [columnCount] columns, and its sidebar when shown.
 *
 * @property columnCount the columns of the content, without the sidebar's
 */
data class ViewLayout(val columnCount: Int, val sections: List<SectionLayout>, val sidebar: SidebarLayout? = null)

/**
 * A sections view's sidebar: its sections stacked in one column, either beside the content ([MODE_COLUMN]) or,
 * on narrow screens, behind a switch between the content and the sidebar ([MODE_TABS]).
 */
data class SidebarLayout(
    val sections: List<SectionLayout>,
    val mode: String,
    val contentLabel: String,
    val sidebarLabel: String,
) {
    companion object {
        const val MODE_COLUMN = "column"
        const val MODE_TABS = "tabs"
    }
}

/** A view's `sidebar` config: its sections and when it is shown. */
data class ViewSidebar(
    val groups: List<CardGroup>,
    val visibility: List<JsonObject>,
    val contentLabel: String,
    val sidebarLabel: String,
)

/** The sidebar of a sections view, if it has one. */
fun viewSidebar(view: ViewConfig): ViewSidebar? {
    val sidebar = view.json.obj("sidebar") ?: return null
    return ViewSidebar(
        groups = cardGroups(
            ViewConfig(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("sections"),
                        "sections" to (sidebar["sections"] ?: JsonArray(emptyList())),
                    ),
                ),
            ),
        ),
        visibility = sidebar.objects("visibility"),
        contentLabel = sidebar.string("content_label").orEmpty(),
        sidebarLabel = sidebar.string("sidebar_label").orEmpty(),
    )
}

/**
 * Lay out the visible parts of [groups] for the current state and screen: hidden sections and cards take no
 * space, sections are placed in up to [ConditionContext.maxColumns] columns, and cards in their section's grid.
 *
 * Port of the layout in frontend@20260624.6 src/panels/lovelace/views/hui-sections-view.ts (column count and
 * section spans) and src/panels/lovelace/sections/hui-grid-section.ts (card spans). Dense
 * section placement is not ported yet. With a [sidebar] that is shown, it takes one of the columns (or becomes a
 * tab on narrow screens), like the sidebar of upstream's sections view.
 */
fun HassSnapshot.viewLayout(
    groups: List<CardGroup>,
    context: ConditionContext,
    sidebar: ViewSidebar? = null,
): ViewLayout {
    val visible = groups.mapNotNull { group -> visibleCards(group, context)?.let { group to it } }
    val spanSum = visible.sumOf { (group, _) -> group.columnSpan }
    val shownSidebar = sidebar?.takeIf { it.visibility.isEmpty() || conditionsMet(it.visibility, context) }
    val narrow = (context.screen?.widthDp ?: 0) <= NARROW_WIDTH_DP
    // The sidebar takes a column of its own, except on narrow screens where it is a tab
    val totalColumns = maxOf(minOf(context.maxColumns ?: 1, spanSum + if (shownSidebar != null) 1 else 0), 1)
    val columnCount = if (shownSidebar != null && !narrow) maxOf(1, totalColumns - 1) else totalColumns
    val sectionCells = placeInGrid(
        visible.map { (group, _) -> minOf(group.columnSpan, columnCount) to group.rowSpan },
        columnCount,
    )
    val sections = visible.zip(sectionCells) { (group, cards), cell ->
        if (group.grid) {
            gridSection(cards, cell)
        } else {
            SectionLayout(cell, 1, cards.mapIndexed { index, card -> PlacedCard(card, GridCell(index, 0, 1, 1), null) })
        }
    }
    return ViewLayout(columnCount, sections, shownSidebar?.let { sidebarLayout(it, context, narrow) })
}

private fun HassSnapshot.sidebarLayout(
    sidebar: ViewSidebar,
    context: ConditionContext,
    narrow: Boolean,
): SidebarLayout {
    val visible = sidebar.groups.mapNotNull { group -> visibleCards(group, context) }
    val sections = visible.mapIndexed { index, cards -> gridSection(cards, GridCell(index, 0, 1, 1)) }
    return SidebarLayout(
        sections = sections,
        mode = if (narrow) SidebarLayout.MODE_TABS else SidebarLayout.MODE_COLUMN,
        contentLabel = sidebar.contentLabel,
        sidebarLabel = sidebar.sidebarLabel,
    )
}

/** Screens up to this width are `narrow` for the frontend (`(max-width: 870px)` in home-assistant-main). */
private const val NARROW_WIDTH_DP = 870

private fun gridSection(cards: List<CardConfig>, cell: GridCell): SectionLayout {
    val columnCount = SECTION_BASE_COLUMNS * cell.columnSpan
    val sizes = cards.map(::cardGridSize)
    val cells = placeInGrid(sizes.map { (it.columns ?: columnCount) to (it.rows ?: 1) }, columnCount)
    val placed = cards.indices.map { PlacedCard(cards[it], cells[it], sizes[it].rows) }
    return SectionLayout(cell, columnCount, placed)
}
