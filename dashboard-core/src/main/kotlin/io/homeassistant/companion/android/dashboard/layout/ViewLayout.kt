package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

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

/** The visible sections of a view laid out in [columnCount] columns. */
data class ViewLayout(val columnCount: Int, val sections: List<SectionLayout>)

/**
 * Lay out the visible parts of [groups] for the current state and screen: hidden sections and cards take no
 * space, sections are placed in up to [ConditionContext.maxColumns] columns, and cards in their section's grid.
 *
 * Port of the layout in frontend@20260624.6 src/panels/lovelace/views/hui-sections-view.ts (column count and
 * section spans) and src/panels/lovelace/sections/hui-grid-section.ts (card spans). The sidebar and dense
 * section placement are not ported yet.
 */
fun HassSnapshot.viewLayout(groups: List<CardGroup>, context: ConditionContext): ViewLayout {
    val visible = groups.mapNotNull { group -> visibleCards(group, context)?.let { group to it } }
    val spanSum = visible.sumOf { (group, _) -> group.columnSpan }
    val columnCount = maxOf(minOf(context.maxColumns ?: 1, spanSum), 1)
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
    return ViewLayout(columnCount, sections)
}

private fun gridSection(cards: List<CardConfig>, cell: GridCell): SectionLayout {
    val columnCount = SECTION_BASE_COLUMNS * cell.columnSpan
    val sizes = cards.map(::cardGridSize)
    val cells = placeInGrid(sizes.map { (it.columns ?: columnCount) to (it.rows ?: 1) }, columnCount)
    val placed = cards.indices.map { PlacedCard(cards[it], cells[it], sizes[it].rows) }
    return SectionLayout(cell, columnCount, placed)
}
