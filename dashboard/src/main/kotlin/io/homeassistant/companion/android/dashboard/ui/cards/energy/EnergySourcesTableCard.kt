package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.energy.SourceRow
import io.homeassistant.companion.android.dashboard.energy.energySourcesTable
import io.homeassistant.companion.android.dashboard.energy.isExternalStatistic
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The energy sources table: each source's energy and cost, the totals, and the compared period's beside them.
 * Port of the rendering of `hui-energy-sources-table-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergySourcesTableCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    val types = remember(card) { card.json.array("types")?.mapNotNull { it.stringOrNull } }
    val onlyTotals = card.json.boolean("show_only_totals") == true
    EnergyCardFrame(card, hass, modifier) { data ->
        val table = remember(data, snapshot.formats) { snapshot.energySourcesTable(data, types, onlyTotals) }
        val localize = { key: String -> snapshot.localize("$TABLE.$key") }
        val header = SourceRow(
            label = localize("source"),
            bullet = null,
            total = false,
            energy = localize("energy"),
            cost = localize("cost"),
            compareEnergy = localize("previous_energy"),
            compareCost = localize("previous_cost"),
        )
        val columns = listOfNotNull(
            SourceRow::compareEnergy.takeIf { table.compare },
            SourceRow::compareCost.takeIf { table.compare && table.showCosts },
            SourceRow::energy,
            SourceRow::cost.takeIf { table.showCosts },
        )
        val widths = columnWidths(listOf(header) + table.rows, columns)
        // Like the frontend's table, it scrolls sideways when its columns don't fit
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE2)) {
            val needed = HADimens.SPACE4 * 2 + BULLET + MIN_LABEL + widths.fold(0.dp) { sum, width -> sum + width } +
                HADimens.SPACE2 * (widths.size + 1)
            val width = if (needed > maxWidth) {
                Modifier.horizontalScroll(rememberScrollState()).width(needed)
            } else {
                Modifier.fillMaxWidth()
            }
            Column(width) {
                TableRow(header, columns, widths, header = true, onClick = null)
                table.rows.forEach { row ->
                    HorizontalDivider(color = LocalHAColorScheme.current.colorBorderNeutralQuiet)
                    val statId = row.statisticId?.takeUnless(::isExternalStatistic)
                    val onClick = statId?.let { id -> { interactions.openMoreInfo(id) } }
                    TableRow(row, columns, widths, header = false, onClick = onClick)
                }
            }
        }
    }
}

/** Each column as wide as its widest cell, like the frontend's table. */
@Composable
private fun columnWidths(rows: List<SourceRow>, columns: List<(SourceRow) -> String>): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = HATextStyle.Body.copy(fontWeight = FontWeight.Bold)
    return remember(rows, columns) {
        columns.map { cell ->
            with(density) {
                rows.maxOf { measurer.measure(cell(it), style, softWrap = false).size.width }.toDp() +
                    CELL_SLACK
            }
        }
    }
}

@Composable
private fun TableRow(
    row: SourceRow,
    columns: List<(SourceRow) -> String>,
    widths: List<Dp>,
    header: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = LocalHAColorScheme.current
    val weight = if (row.total || header) FontWeight.Bold else FontWeight.Normal
    val color = if (header) colors.colorTextSecondary else colors.colorTextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = HADimens.SPACE4),
    ) {
        Box(Modifier.size(BULLET)) { row.bullet?.let { (kind, index) -> Bullet(kind, index) } }
        Text(
            row.label,
            style = HATextStyle.Body,
            fontWeight = weight,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        columns.zip(widths).forEach { (cell, width) ->
            Text(
                cell(row),
                style = HATextStyle.Body,
                fontWeight = weight,
                color = color,
                textAlign = TextAlign.End,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.width(width),
            )
        }
    }
}

/** The source's colour, as a square like the frontend's bullet. */
@Composable
private fun Bullet(kind: String, index: Int) {
    val variable = SOURCE_COLORS[kind] ?: return
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(HARadius.S)
    Box(
        Modifier
            .size(BULLET)
            .background(energyColor(variable, dark, index, background = true, compare = false), shape)
            .border(1.dp, energyColor(variable, dark, index, background = false, compare = false), shape),
    )
}

/** Open the more-info of [entityId], as the frontend's rows do. */
internal fun CardInteractions.openMoreInfo(entityId: String) = onGesture(
    buildJsonObject {
        put("entity", entityId)
        putJsonObject("tap_action") { put("action", JsonPrimitive("more-info")) }
    },
    Gesture.TAP,
)

/** The colour variable of each kind of row (`colorPropertyMap` of the sources table). */
private val SOURCE_COLORS = mapOf(
    "grid_return" to "energy-grid-return-color",
    "grid_consumption" to "energy-grid-consumption-color",
    "battery_in" to "energy-battery-in-color",
    "battery_out" to "energy-battery-out-color",
    "solar" to "energy-solar-color",
    "gas" to "energy-gas-color",
    "water" to "energy-water-color",
)

private const val TABLE = "ui.panel.lovelace.cards.energy.energy_sources_table"
private val ROW_HEIGHT = 44.dp
private val BULLET = 14.dp
private val MIN_LABEL = 112.dp
private val CELL_SLACK = 2.dp
