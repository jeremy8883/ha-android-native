package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

/** Ports of the `getGridOptions` of the built-in cards (src/panels/lovelace/cards/hui-*-card.ts). */
internal fun elementGridOptions(card: CardConfig): GridOptions {
    val json = card.json
    val vertical = json.boolean("vertical") == true
    val features = json.array("features")?.size ?: 0
    return when (card.type) {
        "tile" -> tileGridOptions(json, vertical, features)
        "area" -> areaGridOptions(json, vertical, features)
        "home-summary", "repairs", "updates", "discovered-devices", "toggle-group", "shortcut" ->
            infoTileGridOptions(vertical)
        "button" -> buttonGridOptions(json)
        "thermostat", "humidifier" -> {
            val featureRows = (features * 2 + 2) / 3
            GridOptions(columns = 12, rows = 5 + featureRows, minColumns = 6, minRows = 2 + featureRows)
        }
        "logbook" -> GridOptions(
            columns = 12,
            rows = 6,
            minColumns = 6,
            minRows = if (json.string("title").isNullOrEmpty()) 3 else 4,
        )
        else -> FIXED_GRID_OPTIONS[card.type] ?: GridOptions()
    }
}

/** The tile-style cards (summaries, shortcuts, ...): half width, two rows when vertical. */
private fun infoTileGridOptions(vertical: Boolean): GridOptions = if (vertical) {
    GridOptions(columns = 6, rows = 2, minColumns = 3, minRows = 2)
} else {
    GridOptions(columns = 6, rows = 1, minColumns = 6, minRows = 1)
}

/** The cards whose size doesn't depend on their config. */
private val FIXED_GRID_OPTIONS = mapOf(
    "entity" to GridOptions(columns = 6, rows = 2, minColumns = 6, minRows = 2),
    "sensor" to GridOptions(columns = 6, rows = 2, minColumns = 6, minRows = 2),
    "heading" to GridOptions(fullWidth = true, autoRows = true, minColumns = 3),
    "entities" to GridOptions(columns = 12, autoRows = true, minColumns = 3),
    "distribution" to GridOptions(columns = 12, autoRows = true, minColumns = 3),
    "map" to GridOptions(fullWidth = true, rows = 4, minColumns = 6, minRows = 2),
    "iframe" to GridOptions(fullWidth = true, rows = 4, minColumns = 3, minRows = 2),
    "calendar" to GridOptions(columns = 12, rows = 6, minColumns = 4, minRows = 4),
    "history-graph" to GridOptions(columns = 12, minColumns = 6, minRows = 2),
    "statistics-graph" to GridOptions(columns = 12, minColumns = 6, minRows = 3),
)

private fun tileGridOptions(json: JsonObject, vertical: Boolean, features: Int): GridOptions {
    val inline = features > 0 && featurePosition(json, vertical) == INLINE
    val rows = 1 + (if (features > 0 && !inline) features else 0) + (if (vertical) 1 else 0)
    val minColumns = when {
        vertical -> VERTICAL_MIN_COLUMNS
        inline -> SECTION_COLUMNS
        else -> HALF_COLUMNS
    }
    return GridOptions(columns = HALF_COLUMNS, rows = rows, minColumns = minColumns, minRows = rows)
}

private fun areaGridOptions(json: JsonObject, vertical: Boolean, features: Int): GridOptions {
    val inline = features > 0 && featurePosition(json, vertical) == INLINE
    val pictureRows = when {
        (json.string("display_type") ?: "picture") == "compact" -> 0
        inline -> INLINE_PICTURE_ROWS
        else -> PICTURE_ROWS
    }
    val rows = 1 + (if (features > 0 && !inline) features else 0) + (if (vertical) 1 else 0) + pictureRows
    val minColumns = when {
        vertical -> VERTICAL_MIN_COLUMNS
        inline -> SECTION_COLUMNS
        else -> HALF_COLUMNS
    }
    val columns = if (inline) SECTION_COLUMNS else HALF_COLUMNS
    return GridOptions(columns = columns, rows = rows, minColumns = minColumns, minRows = rows)
}

private fun buttonGridOptions(json: JsonObject): GridOptions {
    // setConfig defaults show_icon and show_name to true
    val showIcon = json.boolean("show_icon") != false
    val showText = json.boolean("show_name") != false || json.boolean("show_state") == true
    return if (showIcon && showText) {
        GridOptions(columns = 6, rows = 2, minColumns = 2, minRows = 2)
    } else {
        GridOptions(columns = 3, rows = 1, minColumns = 2, minRows = 1)
    }
}

private fun featurePosition(json: JsonObject, vertical: Boolean): String =
    if (vertical) BOTTOM else json.string("features_position") ?: BOTTOM

private const val INLINE = "inline"
private const val BOTTOM = "bottom"

/** The columns of a section's grid, half of them, and the fewest a vertical card takes. */
private const val SECTION_COLUMNS = 12
private const val HALF_COLUMNS = 6
private const val VERTICAL_MIN_COLUMNS = 3

/** The rows an area card's picture adds, and with inline features. */
private const val PICTURE_ROWS = 2
private const val INLINE_PICTURE_ROWS = 3
