package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.energy.displayUnit
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.computeHistory
import io.homeassistant.companion.android.dashboard.history.historyLineChart
import io.homeassistant.companion.android.dashboard.history.historyUsesStatistics
import io.homeassistant.companion.android.dashboard.history.historyWithoutAttributes
import io.homeassistant.companion.android.dashboard.history.statisticsChart
import io.homeassistant.companion.android.dashboard.history.timelineChart
import io.homeassistant.companion.android.dashboard.logbook.moreInfoPanelPath
import io.homeassistant.companion.android.dashboard.ui.components.chart.HistoryLineChartView
import io.homeassistant.companion.android.dashboard.ui.components.chart.StatisticsChartView
import io.homeassistant.companion.android.dashboard.ui.components.chart.TimeSpan
import io.homeassistant.companion.android.dashboard.ui.components.chart.TimelineChartView
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.Instant

/**
 * The history section of an entity's details: its last day's states as timelines or lines, or a sensor's 5-minute
 * statistics, kept up to date, with a link to the History panel. Port of `ha-more-info-history`
 * (frontend@20260624.6 src/dialogs/more-info/ha-more-info-history.ts).
 *
 * Upstream: `ha-more-info-history` (src/dialogs/more-info/ha-more-info-history.ts)
 */
@Composable
internal fun MoreInfoHistory(entityId: String, hass: HassSnapshot, now: Instant, interactions: CardInteractions) {
    val viewModel = hiltViewModel<MoreInfoHistoryViewModel>(key = "history-$entityId")
    val request =
        HistoryRequest(entityId, hass.historyUsesStatistics(entityId), hass.historyWithoutAttributes(entityId))
    LaunchedEffect(request) { viewModel.show(request) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        val aggregate = (history as? Loadable.Ready)?.value is EntityHistory.Statistics
        MoreInfoSectionHeader(
            title = hass.localize("$MORE_INFO.history"),
            subtitle = if (aggregate) hass.localize("$MORE_INFO.aggregate") else null,
            showMore = hass.localize("$MORE_INFO.show_more"),
            onShowMore = {
                val path = moreInfoPanelPath("history", entityId, now, hass.formats.zone)
                interactions.onAction(CardAction.Navigate(path, replace = false))
            },
        )
        when (val loaded = history) {
            Loadable.Loading -> HALoading(Modifier.align(Alignment.CenterHorizontally))
            is Loadable.Failed -> HistoryError(hass, LocalContext.current.loadErrorText(loaded.error))
            is Loadable.Ready -> HistoryCharts(entityId, loaded.value, hass, now)
        }
    }
}

/**
 * A section's header in the details: its [title] (with a [subtitle]), and a link to see more on its page.
 *
 * Upstream: the `.header` of `ha-more-info-history`, `ha-more-info-logbook`
 * (src/dialogs/more-info/ha-more-info-history.ts; src/dialogs/more-info/ha-more-info-logbook.ts)
 */
@Composable
internal fun MoreInfoSectionHeader(
    title: String,
    subtitle: String?,
    showMore: String,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHAColorScheme.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
            )
            subtitle?.let { Text(it, style = Css.Text.Body.copy(textAlign = TextAlign.Start)) }
        }
        HAPlainButton(showMore, onShowMore)
    }
}

/**
 * Why the history couldn't be loaded.
 *
 * Upstream: part of `ha-more-info-history` (src/dialogs/more-info/ha-more-info-history.ts)
 */
@Composable
private fun HistoryError(hass: HassSnapshot, message: String) {
    Text(
        "${hass.localize("ui.components.history_charts.error")}: $message",
        style = Css.Text.Body.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorOnDangerNormal,
    )
}

/**
 * The charts of [history]: a timeline or lines of states, or the statistics.
 *
 * Upstream: `state-history-charts`, `statistics-chart` (src/components/chart/state-history-charts.ts;
 * src/components/chart/statistics-chart.ts)
 */
@Composable
private fun HistoryCharts(entityId: String, history: EntityHistory, hass: HassSnapshot, now: Instant) {
    val dark = isSystemInDarkTheme()
    val end = now.toEpochMilli().toDouble()
    when (history) {
        is EntityHistory.Statistics -> {
            val chart = remember(history, hass, end) {
                val unit = hass.displayUnit(entityId, history.statistics.metadata)
                hass.statisticsChart(entityId, history.statistics.stats, unit, end)
            }
            StatisticsChartView(chart, hass.formats, dark, zoomResetLabel = hass.localize(ZOOM_RESET))
        }
        is EntityHistory.States -> {
            val result = remember(history, hass) { hass.computeHistory(history.history, listOf(entityId)) }
            // From the first state to now, like `state-history-charts` up to now
            val start = (
                result.timeline.mapNotNull { it.data.firstOrNull()?.lastChanged } +
                    result.line.flatMap { unit -> unit.data.mapNotNull { it.states.firstOrNull()?.lastChanged } }
                )
                .minOrNull()?.toDouble()?.coerceAtMost(end) ?: end
            val time = TimeSpan(start, end)
            if (result.timeline.isNotEmpty()) {
                val timelines = remember(result, end) { result.timeline.map { hass.timelineChart(it, end) } }
                TimelineChartView(
                    timelines,
                    time,
                    hass.formats,
                    dark,
                    durationLabel = hass.localize("ui.components.history_charts.duration"),
                    zoomResetLabel = hass.localize(ZOOM_RESET),
                )
            }
            result.line.forEach { unit ->
                val chart = remember(unit, end) { hass.historyLineChart(unit.data, end, end) }
                HistoryLineChartView(
                    chart,
                    unit.unit,
                    time,
                    hass.formats,
                    dark,
                    zoomResetLabel = hass.localize(ZOOM_RESET),
                )
            }
        }
    }
}

internal const val MORE_INFO = "ui.dialogs.more_info_control"

/** Each chart zooms on its own in the details, with its own reset button. */
private const val ZOOM_RESET = "ui.components.history_charts.zoom_reset"
