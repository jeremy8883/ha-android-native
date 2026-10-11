package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.homeassistant.companion.android.dashboard.ui.components.chart.ChartLegend
import io.homeassistant.companion.android.dashboard.ui.components.chart.ChartLegendItem
import io.homeassistant.companion.android.dashboard.ui.components.chart.legendTexts
import io.homeassistant.companion.android.dashboard.ui.theme.FrontendTextStyle
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * The energy usage graph: by period, where the home's energy came from and where the rest went, with the total in
 * the header. Port of the rendering of `hui-energy-usage-graph-card` (frontend@20260624.6).
 *
 * Upstream: `hui-energy-usage-graph-card` (src/panels/lovelace/cards/energy/hui-energy-usage-graph-card.ts)
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
            total?.let {
                TotalChip(
                    localize("$GRAPH.total_usage", mapOf("num" to number(it))),
                    tooltip = localize("$GRAPH.total_consumed", mapOf("num" to number(it))),
                )
            }
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
                    style = FrontendTextStyle.Body,
                    color = LocalHAColorScheme.current.colorTextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (card.json.boolean("show_legend") != false) {
            Legend(model.chart, hidden, snapshot, onHiddenChange = { hidden = it })
        }
    }
}

/**
 * The chip next to the title (`hui-energy-graph-chip`): [text] in an outlined box, showing [tooltip] on a long press
 * (`ha-tooltip`, which shows on hover upstream).
 *
 * Upstream: `hui-energy-graph-chip`, with `ha-tooltip` (src/panels/lovelace/cards/energy/common/hui-energy-graph-chip.ts;
 * src/components/ha-tooltip.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TotalChip(text: String, tooltip: String) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(HARadius.M)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                shape = shape,
                containerColor = colors.colorSurfaceDefault,
                contentColor = colors.colorTextPrimary,
                shadowElevation = TOOLTIP_ELEVATION,
            ) {
                Text(tooltip, style = HATextStyle.BodyMedium, modifier = Modifier.padding(HADimens.SPACE1))
            }
        },
        state = rememberTooltipState(),
    ) {
        Text(
            text,
            style = HATextStyle.BodyMedium,
            color = colors.colorTextPrimary,
            modifier = Modifier
                .border(1.dp, resolveVariable("divider-color", dark) ?: colors.colorBorderNeutralQuiet, shape)
                .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
        )
    }
}

/**
 * An item per shown series; a tap hides or shows it and its compared series, a long press shows only it. A tap on a
 * name for which [opensLabel] is true calls [onOpen] instead. Port of the custom legend.
 *
 * Upstream: part of `ha-chart-base` (src/components/chart/ha-chart-base.ts)
 */
@Composable
internal fun Legend(
    chart: EnergyBarChart,
    hidden: Set<String>,
    hass: HassSnapshot,
    onHiddenChange: (Set<String>) -> Unit,
    opensLabel: (String) -> Boolean = { false },
    onOpen: (String) -> Unit = {},
) {
    val fallback = LocalHAColorScheme.current.colorTextSecondary
    val dark = isSystemInDarkTheme()
    val entries = chart.series.filterNot { it.compare }.map { series ->
        ChartLegendItem(
            series.id,
            series.name,
            // The bars' fill, as the legend takes the series' itemStyle colour
            seriesColor(series, dark, background = true) ?: fallback,
            opensLabel = opensLabel(series.id),
        )
    }
    LegendItems(entries, hidden, hass, onHiddenChange, onOpen)
}

/**
 * The custom legend of the frontend's energy charts ([ChartLegend]) under a chart.
 *
 * Upstream: part of `ha-chart-base` (src/components/chart/ha-chart-base.ts)
 */
@Composable
internal fun LegendItems(
    entries: List<ChartLegendItem>,
    hidden: Set<String>,
    hass: HassSnapshot,
    onHiddenChange: (Set<String>) -> Unit,
    onOpen: (String) -> Unit = {},
) {
    ChartLegend(
        items = entries,
        hidden = hidden,
        texts = hass.legendTexts(),
        onHiddenChange = onHiddenChange,
        modifier = Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, bottom = HADimens.SPACE4),
        onOpen = onOpen,
    )
}

private const val GRAPH = "ui.panel.lovelace.cards.energy.energy_usage_graph"
private val TOOLTIP_ELEVATION = 4.dp
