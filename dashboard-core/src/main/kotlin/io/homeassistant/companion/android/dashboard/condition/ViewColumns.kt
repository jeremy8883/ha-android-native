package io.homeassistant.companion.android.dashboard.condition

/**
 * The number of columns a sections view uses at [widthDp], which `view_columns` conditions compare against.
 *
 * Port of the `_columnsController` callback of frontend@20260624.6 src/panels/lovelace/views/hui-sections-view.ts:
 * columns of at least 320px separated by the column gap (8px up to 600px wide, 32px above), clamped to the view's
 * `max_columns` (default 4).
 */
fun sectionsViewColumns(widthDp: Int, maxColumns: Int?): Int {
    if (widthDp <= 0) return 1
    val gap = if (widthDp <= NARROW_MAX_WIDTH) NARROW_COLUMN_GAP else COLUMN_GAP
    val columns = (widthDp + gap) / (COLUMN_MIN_WIDTH + gap)
    return columns.coerceIn(1, maxOf(1, maxColumns ?: DEFAULT_MAX_COLUMNS))
}

private const val COLUMN_MIN_WIDTH = 320
private const val COLUMN_GAP = 32
private const val NARROW_COLUMN_GAP = 8
private const val NARROW_MAX_WIDTH = 600
private const val DEFAULT_MAX_COLUMNS = 4
