package io.homeassistant.companion.android.dashboard.ui.panels

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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.HistoryLineChart
import io.homeassistant.companion.android.dashboard.history.HistoryResult
import io.homeassistant.companion.android.dashboard.history.SeriesColor
import io.homeassistant.companion.android.dashboard.history.TimelineChart
import io.homeassistant.companion.android.dashboard.history.historyLegend
import io.homeassistant.companion.android.dashboard.history.historyLineChart
import io.homeassistant.companion.android.dashboard.history.timelineChart
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.energy.graphColorVariable
import io.homeassistant.companion.android.dashboard.ui.charts.ChartText
import io.homeassistant.companion.android.dashboard.ui.charts.ChartZoomState
import io.homeassistant.companion.android.dashboard.ui.charts.HistoryLegend
import io.homeassistant.companion.android.dashboard.ui.charts.HistoryLegendTexts
import io.homeassistant.companion.android.dashboard.ui.charts.HistoryLineChartView
import io.homeassistant.companion.android.dashboard.ui.charts.LABEL_GAP
import io.homeassistant.companion.android.dashboard.ui.charts.TimeSpan
import io.homeassistant.companion.android.dashboard.ui.charts.TimelineChartView
import io.homeassistant.companion.android.dashboard.ui.charts.timelineNamesWidth
import io.homeassistant.companion.android.dashboard.ui.charts.valueAxisWidth
import io.homeassistant.companion.android.dashboard.ui.charts.valueTicks
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.Instant

/**
 * The charts of `state-history-charts` with `sync-charts`: the timelines with the entities' names, then each line
 * chart with its legend, divided, all zoomed together ([zoom]), all plots starting at the same distance from the left (`paddingYAxis`), from the
 * period's start to its end (now, when the end is ahead).
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
    val inset = chartsInset(timelines, lines, text)
    val legendTexts = HistoryLegendTexts(
        toggle = hass.localize("$CHARTS.toggle_visibility"),
        more = hass.localize("$CHARTS.expand_legend"),
        less = hass.localize("$CHARTS.collapse_legend"),
    )
    Column {
        if (timelines.isNotEmpty()) {
            TimelineChartView(timelines, time, hass.formats, dark, showNames = true, minLeft = inset, zoom = zoom)
        }
        history.line.forEachIndexed { index, unit ->
            if (index > 0 || timelines.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = HADimens.SPACE4),
                    thickness = DIVIDER,
                    color = LocalHAColorScheme.current.colorBorderNeutralQuiet,
                )
            }
            var hidden by rememberSaveable(unit.identifier) { mutableStateOf(emptySet<String>()) }
            val chart = lines[index]
            Column(Modifier.padding(top = HADimens.SPACE2)) {
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
                HistoryLegend(
                    items = remember(chart, hass) { hass.historyLegend(chart) },
                    colors = remember(chart, dark) { legendColors(chart, dark) },
                    hidden = hidden,
                    texts = legendTexts,
                    onToggle = { id -> hidden = if (id in hidden) hidden - id else hidden + id },
                    onOpenEntity = { interactions.onAction(CardAction.MoreInfo(it)) },
                )
            }
        }
    }
}

/**
 * How far from the left every plot starts (`paddingYAxis`): past the timelines' names and the widest value axis.
 */
@Composable
private fun chartsInset(timelines: List<TimelineChart>, lines: List<HistoryLineChart>, text: ChartText): Dp =
    with(LocalDensity.current) {
        val names = if (timelines.isEmpty()) 0f else text.timelineNamesWidth(timelines.map { it.name })
        val axes = lines.maxOfOrNull { chart ->
            val values = chart.series.flatMap { s -> s.points.mapNotNull { it.y } }
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
