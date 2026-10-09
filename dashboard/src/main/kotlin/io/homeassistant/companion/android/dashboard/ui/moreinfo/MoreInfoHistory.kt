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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
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
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.charts.HistoryLineChartView
import io.homeassistant.companion.android.dashboard.ui.charts.StatisticsChartView
import io.homeassistant.companion.android.dashboard.ui.charts.TimeSpan
import io.homeassistant.companion.android.dashboard.ui.charts.TimelineChartView
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import java.time.Instant

/**
 * The history section of an entity's details: its last day's states as timelines or lines, or a sensor's 5-minute
 * statistics, kept up to date, with a link to the History panel. Port of `ha-more-info-history`
 * (frontend@20260624.6 src/dialogs/more-info/ha-more-info-history.ts).
 */
@Composable
internal fun MoreInfoHistory(entityId: String, hass: HassSnapshot, now: Instant, interactions: CardInteractions) {
    val viewModel = hiltViewModel<MoreInfoHistoryViewModel>(key = "history-$entityId")
    val request =
        HistoryRequest(entityId, hass.historyUsesStatistics(entityId), hass.historyWithoutAttributes(entityId))
    LaunchedEffect(request) { viewModel.show(request) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
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

/** A section's header in the details: its [title] (with a [subtitle]), and a link to see more on its page. */
@Composable
internal fun MoreInfoSectionHeader(title: String, subtitle: String?, showMore: String, onShowMore: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HATextStyle.Body.copy(textAlign = TextAlign.Start), color = colors.colorTextPrimary)
            subtitle?.let { Text(it, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start)) }
        }
        HAPlainButton(showMore, onShowMore)
    }
}

@Composable
private fun HistoryError(hass: HassSnapshot, message: String) {
    Text(
        "${hass.localize("ui.components.history_charts.error")}: $message",
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorOnDangerNormal,
    )
}

/** The charts of [history]: a timeline or lines of states, or the statistics. */
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
            StatisticsChartView(chart, hass.formats, dark)
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
                TimelineChartView(timelines, time, hass.formats, dark)
            }
            result.line.forEach { unit ->
                val chart = remember(unit, end) { hass.historyLineChart(unit.data, end, end) }
                HistoryLineChartView(chart, unit.unit, time, hass.formats, dark)
            }
        }
    }
}

internal const val MORE_INFO = "ui.dialogs.more_info_control"
