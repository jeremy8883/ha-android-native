package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyBarChart
import io.homeassistant.companion.android.dashboard.energy.energyTooltip
import io.homeassistant.companion.android.dashboard.energy.energyUsageGraph
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * The energy usage graph: by period, where the home's energy came from and where the rest went, with the total in
 * the header. Port of the rendering of `hui-energy-usage-graph-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergyUsageGraphCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    val localize = snapshot.localize
    fun number(value: Double) = snapshot.formats.number(BigDecimal.valueOf(value), 0, 2)
    EnergyCardFrame(
        card = card,
        hass = hass,
        modifier = modifier,
        headerEnd = { data ->
            val total = remember(data) { snapshot.energyUsageGraph(data).total }
            total?.let { TotalChip(localize("$GRAPH.total_usage", mapOf("num" to number(it)))) }
        },
    ) { data ->
        val model = remember(data, snapshot.formats) { snapshot.energyUsageGraph(data) }
        var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
        Box(Modifier.padding(HADimens.SPACE4)) {
            EnergyBarChartView(
                chart = model.chart,
                formats = snapshot.formats,
                hidden = hidden,
                tooltip = { series, start -> snapshot.formats.energyTooltip(model.chart, series, start) },
                formatTotal = { localize("$GRAPH.total_consumed", mapOf("num" to number(it))) },
            )
            if (model.chart.isEmpty) {
                val today = now.value?.toLocalDate()
                val key = if (data.period.start == today) "no_data" else "no_data_period"
                Text(
                    localize("ui.panel.lovelace.cards.energy.$key"),
                    style = HATextStyle.Body,
                    color = LocalHAColorScheme.current.colorTextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (card.json.boolean("show_legend") != false) {
            Legend(model.chart, hidden) { id -> hidden = if (id in hidden) hidden - id else hidden + id }
        }
    }
}

/** The chip next to the title (`hui-energy-graph-chip`). */
@Composable
internal fun TotalChip(text: String) {
    val colors = LocalHAColorScheme.current
    Text(
        text,
        style = HATextStyle.Body,
        color = colors.colorTextSecondary,
        modifier = Modifier
            .background(colors.colorFillNeutralQuietResting, RoundedCornerShape(HARadius.Pill))
            .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
    )
}

/** An item per shown series; a tap hides or shows it and its compared series. Port of the custom legend. */
@Composable
internal fun Legend(chart: EnergyBarChart, hidden: Set<String>, onToggle: (String) -> Unit) {
    val fallback = LocalHAColorScheme.current.colorTextSecondary
    val dark = isSystemInDarkTheme()
    val entries = chart.series.filterNot { it.compare }.map { series ->
        LegendEntry(series.id, series.name, seriesColor(series, dark, background = false) ?: fallback)
    }
    LegendItems(entries, hidden, onToggle)
}

/** A legend item: what a tap toggles ([id]), its [name] and [color]. */
internal class LegendEntry(val id: String, val name: String, val color: Color)

/** The custom legend of the frontend's charts: an item per entry, crossed out while [hidden]. */
@Composable
internal fun LegendItems(entries: List<LegendEntry>, hidden: Set<String>, onToggle: (String) -> Unit) {
    val colors = LocalHAColorScheme.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
        modifier = Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, bottom = HADimens.SPACE4),
    ) {
        entries.forEach { entry ->
            val off = entry.id in hidden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                modifier = Modifier.clickable(role = Role.Checkbox) { onToggle(entry.id) },
            ) {
                Box(
                    Modifier.size(LEGEND_MARKER)
                        .background(if (off) colors.colorTextDisabled else entry.color, RoundedCornerShape(HARadius.S)),
                )
                Text(
                    entry.name,
                    style = HATextStyle.Body,
                    color = if (off) colors.colorTextDisabled else colors.colorTextSecondary,
                    textDecoration = if (off) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

private const val GRAPH = "ui.panel.lovelace.cards.energy.energy_usage_graph"
private val LEGEND_MARKER = 12.dp
