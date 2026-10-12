package io.homeassistant.companion.android.dashboard.ui.components.chart

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.HistoryLineChart
import io.homeassistant.companion.android.dashboard.history.HistoryResult
import io.homeassistant.companion.android.dashboard.history.SeriesColor
import io.homeassistant.companion.android.dashboard.history.TimelineChart
import io.homeassistant.companion.android.dashboard.history.historyLegend
import io.homeassistant.companion.android.dashboard.history.historyLineChart
import io.homeassistant.companion.android.dashboard.history.inZoomWindow
import io.homeassistant.companion.android.dashboard.history.timelineChart
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.energy.graphColorVariable
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.Instant

/**
 * The charts of `state-history-charts` with `sync-charts`: the timelines with the entities' names, then each line
 * chart with its legend, divided, all zoomed together ([zoom]), all plots starting at the same distance from the left (`paddingYAxis`), from the
 * period's start to its end (now, when the end is ahead).
 *
 * Upstream: `state-history-charts` (src/components/chart/state-history-charts.ts)
 */
@Composable
internal fun HistoryChartsList(
    history: HistoryResult,
    hass: HassSnapshot,
    range: DateRange,
    now: Instant,
    interactions: CardInteractions,
    zoom: ChartZoomState,
) {
    val dark = isSystemInDarkTheme()
    val nowMillis = now.toEpochMilli().toDouble()
    val end = minOf(range.end.toEpochMilli().toDouble(), nowMillis)
    val time = TimeSpan(range.start.toEpochMilli().toDouble(), end)
    val timelines = remember(history, hass, end) { history.timeline.map { hass.timelineChart(it, end) } }
    val lines =
        remember(history, hass, end) {
            history.line.map { hass.historyLineChart(it.data, end, nowMillis, showNames = true) }
        }
    val text = ChartText(
        rememberTextMeasurer(),
        TextStyle(fontSize = ChartText.FONT_SIZE),
        hass.formats,
        LocalDensity.current.density,
    )
    val inset = chartsInset(timelines, lines, text, time.zoomed(zoom.zoom).shown)
    val legendTexts = hass.legendTexts()
    Column {
        if (timelines.isNotEmpty()) {
            TimelineChartView(
                timelines,
                time,
                hass.formats,
                dark,
                durationLabel = hass.localize("$CHARTS.duration"),
                showNames = true,
                minLeft = inset,
                zoom = zoom,
            )
        }
        history.line.forEachIndexed { index, unit ->
            if (index > 0 || timelines.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = Css.Space.S4),
                    thickness = DIVIDER,
                    color = LocalHAColorScheme.current.colorBorderNeutralQuiet,
                )
            }
            var hidden by rememberSaveable(unit.identifier) { mutableStateOf(emptySet<String>()) }
            val chart = lines[index]
            Column(Modifier.padding(top = Css.Space.S2)) {
                HistoryLineChartView(
                    chart,
                    unit.unit,
                    time,
                    hass.formats,
                    dark,
                    hidden = hidden,
                    minLeft = inset,
                    zoom = zoom,
                )
                LineLegend(chart, hass, dark, hidden, legendTexts, interactions) { hidden = it }
            }
        }
    }
}

/**
 * A line chart's legend: its lines, a tap on a name opening its entity.
 *
 * Upstream: part of `state-history-chart-line` (src/components/chart/state-history-chart-line.ts)
 */
@Composable
private fun LineLegend(
    chart: HistoryLineChart,
    hass: HassSnapshot,
    dark: Boolean,
    hidden: Set<String>,
    texts: HistoryLegendTexts,
    interactions: CardInteractions,
    onHiddenChange: (Set<String>) -> Unit,
) {
    val legend = remember(chart, hass) { hass.historyLegend(chart) }
    val colors = remember(chart, dark) { legendColors(chart, dark) }
    ChartLegend(
        items = legend.map { ChartLegendItem(it.id, it.name, colors[it.id], it.value, opensLabel = true) },
        hidden = hidden,
        texts = texts,
        onHiddenChange = onHiddenChange,
        onOpen = { id ->
            legend.firstOrNull { it.id == id }?.let { interactions.onAction(CardAction.MoreInfo(it.entityId)) }
        },
    )
}

/**
 * How far from the left every plot starts (`paddingYAxis`): past the timelines' names and the widest value axis of
 * what [shown] of the time shows, so a zoom whose values need wider labels moves every plot along, as upstream's.
 *
 * Upstream: part of `state-history-charts` (src/components/chart/state-history-charts.ts)
 */
@Composable
private fun chartsInset(
    timelines: List<TimelineChart>,
    lines: List<HistoryLineChart>,
    text: ChartText,
    shown: TimeSpan,
): Dp = with(LocalDensity.current) {
    val names = if (timelines.isEmpty()) 0f else text.timelineNamesWidth(timelines.map { it.name })
    val axes = lines.maxOfOrNull { chart ->
        val values = chart.series.flatMap { s ->
            s.points.inZoomWindow(shown.start, shown.end) { it.x }.mapNotNull { it.y }
        }
        valueAxisWidth(valueTicks(values), chart.yFractionDigits, text) + LABEL_GAP.toPx()
    } ?: 0f
    maxOf(names, axes).toDp()
}

/** Each series' colour, by its id. */
private fun legendColors(chart: HistoryLineChart, dark: Boolean) = chart.series.mapNotNull { series ->
    when (val color = series.color) {
        is SeriesColor.Palette -> resolveVariable(graphColorVariable(color.index), dark)
        is SeriesColor.Variable -> resolveVariable(color.name, dark)
    }?.let { series.id to it }
}.toMap()

private const val CHARTS = "ui.components.history_charts"
private val DIVIDER = 2.dp
