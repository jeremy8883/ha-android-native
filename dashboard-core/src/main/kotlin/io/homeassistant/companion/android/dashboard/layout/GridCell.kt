package io.homeassistant.companion.android.dashboard.layout

/** Where an item sits in a grid: zero-based row and column, and the rows and columns it spans. */
data class GridCell(val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int)

/**
 * Place items of the given (columns, rows) spans in a grid of [columnCount] columns, as CSS grid's sparse
 * auto-placement does with `grid-auto-flow: row`: each item goes to the first position after the previous one
 * where it fits, wrapping to the next row. Spans wider than the grid are narrowed to it, like the
 * `span min(size, column-count)` rules upstream.
 */
fun placeInGrid(spans: List<Pair<Int, Int>>, columnCount: Int): List<GridCell> {
    require(columnCount > 0) { "A grid needs at least one column" }
    val occupied = mutableListOf<BooleanArray>()
    fun free(row: Int, column: Int, rowSpan: Int, columnSpan: Int): Boolean = (row until row + rowSpan).all { r ->
        val cells = occupied.getOrNull(r)
        cells == null || (column until column + columnSpan).none { cells[it] }
    }

    var cursorRow = 0
    var cursorColumn = 0
    return spans.map { (columns, rows) ->
        val columnSpan = columns.coerceIn(1, columnCount)
        val rowSpan = rows.coerceAtLeast(1)
        var row = cursorRow
        var column = cursorColumn
        while (column + columnSpan > columnCount || !free(row, column, rowSpan, columnSpan)) {
            column++
            if (column + columnSpan > columnCount) {
                row++
                column = 0
            }
        }
        for (r in row until row + rowSpan) {
            while (occupied.size <= r) occupied += BooleanArray(columnCount)
            for (c in column until column + columnSpan) occupied[r][c] = true
        }
        cursorRow = row
        cursorColumn = column + columnSpan
        GridCell(row, column, rowSpan, columnSpan)
    }
}
