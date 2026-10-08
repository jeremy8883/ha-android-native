package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.homeassistant.companion.android.dashboard.layout.GridCell

/**
 * Lays [content] out like a CSS grid with [columnCount] equal columns: the n-th child goes to `cells[n]`.
 * Rows are as tall as their tallest single-row child; a child spanning several rows grows the last of them when
 * it needs more room. Children are measured at their cell's width.
 */
@Composable
internal fun DashboardGrid(
    cells: List<GridCell>,
    columnCount: Int,
    columnGap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val columnGapPx = columnGap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val width = constraints.maxWidth
        val columnWidth = (width - columnGapPx * (columnCount - 1)).coerceAtLeast(0) / columnCount.toFloat()
        fun spanWidth(cell: GridCell) =
            (columnWidth * cell.columnSpan + columnGapPx * (cell.columnSpan - 1)).toInt().coerceAtLeast(0)

        val placeables = measurables.zip(cells) { measurable, cell ->
            measurable.measure(Constraints.fixedWidth(spanWidth(cell)))
        }
        val rowCount = cells.maxOfOrNull { it.row + it.rowSpan } ?: 0
        val rowHeights = IntArray(rowCount)
        cells.forEachIndexed { index, cell ->
            if (cell.rowSpan == 1) rowHeights[cell.row] = maxOf(rowHeights[cell.row], placeables[index].height)
        }
        cells.forEachIndexed { index, cell ->
            if (cell.rowSpan > 1) {
                val rows = cell.row until cell.row + cell.rowSpan
                val available = rows.sumOf { rowHeights[it] } + rowGapPx * (cell.rowSpan - 1)
                val missing = placeables[index].height - available
                if (missing > 0) rowHeights[rows.last] += missing
            }
        }
        val rowTops = IntArray(rowCount)
        for (row in 1 until rowCount) rowTops[row] = rowTops[row - 1] + rowHeights[row - 1] + rowGapPx
        val height = if (rowCount == 0) 0 else rowTops.last() + rowHeights.last()

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val cell = cells[index]
                val x = (columnWidth * cell.column + columnGapPx * cell.column).toInt()
                placeable.place(x, rowTops[cell.row])
            }
        }
    }
}
