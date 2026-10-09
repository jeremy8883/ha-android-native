package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The size of a card in a section's 12-column grid.
 *
 * @property columns columns spanned, `null` for the full width
 * @property rows rows of [SECTION_ROW_HEIGHT_DP] spanned, `null` to size by content
 */
data class CardGridSize(val columns: Int?, val rows: Int?)

/** Height of a grid row in a section, in dp (`--ha-section-grid-row-height`). */
const val SECTION_ROW_HEIGHT_DP = 56

/** Gap between grid rows and columns in a section, in dp (`--ha-section-grid-row-gap`). */
const val SECTION_GRID_GAP_DP = 8

/** Columns of a section's grid per column the section spans (`--base-column-count`). */
const val SECTION_BASE_COLUMNS = 12

/**
 * The grid size of [card]: the card type's own options overridden by its `grid_options` (or legacy
 * `layout_options`), then clamped to the min and max.
 *
 * Ports of `hui-card.getGridOptions` and `computeCardGridSize` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-card.ts, src/panels/lovelace/common/compute-card-grid-size.ts).
 */
fun cardGridSize(card: CardConfig): CardGridSize {
    val options = elementGridOptions(card).merge(configGridOptions(card.json))
    val rows = options.rows?.let { clamp(it, options.minRows, options.maxRows) }
    val columns = options.columns?.let { clamp(it, options.minColumns, options.maxColumns) }
    return CardGridSize(
        columns = if (options.fullWidth) null else columns ?: SECTION_BASE_COLUMNS,
        rows = if (options.autoRows) null else rows,
    )
}

/** `LovelaceGridOptions`; `fullWidth` and `autoRows` stand for the string values "full" and "auto". */
internal data class GridOptions(
    val columns: Int? = null,
    val rows: Int? = null,
    val minColumns: Int? = null,
    val maxColumns: Int? = null,
    val minRows: Int? = null,
    val maxRows: Int? = null,
    val fullWidth: Boolean = false,
    val autoRows: Boolean = false,
    val hasColumns: Boolean = columns != null || fullWidth,
    val hasRows: Boolean = rows != null || autoRows,
) {
    /** `{...this, ...other}`: keys [other] sets win. */
    fun merge(other: GridOptions) = GridOptions(
        columns = if (other.hasColumns) other.columns else columns,
        rows = if (other.hasRows) other.rows else rows,
        minColumns = other.minColumns ?: minColumns,
        maxColumns = other.maxColumns ?: maxColumns,
        minRows = other.minRows ?: minRows,
        maxRows = other.maxRows ?: maxRows,
        fullWidth = if (other.hasColumns) other.fullWidth else fullWidth,
        autoRows = if (other.hasRows) other.autoRows else autoRows,
    )
}

/** Port of `getConfigGridOptions`, migrating `layout_options` (`grid_columns` × 3) like upstream. */
private fun configGridOptions(json: JsonObject): GridOptions =
    json.obj("grid_options")?.let(::gridOptions) ?: json.obj("layout_options")?.let(::layoutGridOptions)
        ?: GridOptions()

private fun gridOptions(options: JsonObject) = GridOptions(
    columns = options.int("columns"),
    rows = options.int("rows"),
    minColumns = options.int("min_columns"),
    maxColumns = options.int("max_columns"),
    minRows = options.int("min_rows"),
    maxRows = options.int("max_rows"),
    fullWidth = options.string("columns") == FULL,
    autoRows = options.string("rows") == AUTO,
)

private fun layoutGridOptions(layout: JsonObject) = GridOptions(
    columns = layout.int("grid_columns")?.times(LAYOUT_COLUMN_MULTIPLIER),
    rows = layout.int("grid_rows"),
    minColumns = layout.int("grid_min_columns")?.times(LAYOUT_COLUMN_MULTIPLIER),
    maxColumns = layout.int("grid_max_columns")?.times(LAYOUT_COLUMN_MULTIPLIER),
    minRows = layout.int("grid_min_rows"),
    maxRows = layout.int("grid_max_rows"),
    fullWidth = layout.string("grid_columns") == FULL,
    autoRows = layout.string("grid_rows") == AUTO,
)

/** Port of `conditionalClamp`. */
private fun clamp(value: Int, min: Int?, max: Int?): Int {
    var result = if (min != null) maxOf(value, min) else value
    if (max != null) result = minOf(result, max)
    return result
}

private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)
    ?.takeUnless { it.isString }?.doubleOrNull?.toInt()

private const val FULL = "full"
private const val AUTO = "auto"
private const val LAYOUT_COLUMN_MULTIPLIER = 3
