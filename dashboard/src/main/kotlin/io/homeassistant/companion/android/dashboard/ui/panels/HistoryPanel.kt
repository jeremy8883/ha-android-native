package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.ChartZoom
import io.homeassistant.companion.android.dashboard.history.HistoryResult
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.panels.RangePreset
import io.homeassistant.companion.android.dashboard.panels.historyLogbookQuery
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.target.resolveEntityIds
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.charts.ChartZoomState
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.target.TargetPicker
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Port of `ha-panel-history` on a phone: the period (with the extended presets) and what to show, then their
 * history as `state-history-charts` draws it: the timelines, then a line chart per unit and device class, the hourly
 * statistics before the period's states drawn faintly. [query] is the panel's address's (as the details' "Show more"
 * opens it); "Remove all selections" and the menu (download as CSV) go in the [toolbar].
 *
 * Upstream: `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
internal fun HistoryPanel(
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    query: String?,
    toolbar: PanelToolbar,
    interactions: CardInteractions,
) {
    val snapshot = hass.value ?: return
    val current = now.value ?: return
    val initial = remember(query) { historyLogbookQuery(query) }
    var range by rememberSaveable(query, stateSaver = DateRangeSaver) {
        val around = DateRange.aroundNow(current)
        mutableStateOf(DateRange(initial.start ?: around.start, initial.end ?: around.end))
    }
    var target by rememberSaveable(query, stateSaver = TargetSaver) { mutableStateOf(initial.target ?: Target()) }
    val viewModel = hiltViewModel<HistoryPanelViewModel>(key = "history-panel")
    // Opened without a target in the address, it shows what was picked last time, as the frontend does
    var restored by rememberSaveable(query) { mutableStateOf(initial.target != null) }
    LaunchedEffect(query) {
        if (!restored) viewModel.pickedTarget()?.let { target = it }
        restored = true
    }
    // Port of `_getEntityIds`: what the target stands for
    val entityIds = remember(target, snapshot.registries, snapshot.states.keys) { snapshot.resolveEntityIds(target) }
    LaunchedEffect(entityIds, range, restored) {
        if (restored) viewModel.show(HistoryPanelRequest(entityIds, range.start, range.end, snapshot))
    }
    val state by viewModel.history.collectAsStateWithLifecycle()
    val pick: (Target) -> Unit = {
        target = it
        viewModel.savePickedTarget(it)
    }
    HistoryToolbar(toolbar, snapshot, state, hasTarget = entityIds.isNotEmpty(), onRemoveAll = {
        pick(Target())
    }, viewModel)
    // All the charts zoom together (`sync-charts`); a new period shows all of it
    val zoom = remember(range) { ChartZoomState() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "filters") {
                Column(Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, top = HADimens.SPACE4)) {
                    // Neither can be changed while the history loads
                    val enabled = state != HistoryPanelState.Loading
                    DateRangeField(
                        range,
                        snapshot,
                        current,
                        RangePreset.EXTENDED,
                        onChange = { range = it },
                        enabled = enabled,
                    )
                    TargetPicker(
                        target,
                        snapshot,
                        entityFilter = { true },
                        onChange = pick,
                        modifier = Modifier.padding(top = HADimens.SPACE2),
                        enabled = enabled,
                    )
                }
            }
            item(key = "charts") {
                Box(Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, bottom = HADimens.SPACE4)) {
                    HistoryContent(state, snapshot, range, current.toInstant(), interactions, zoom)
                }
            }
        }
        HistoryZoomReset(zoom, snapshot.localize("$CHARTS.zoom_reset"), Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * The charts' one "Reset zoom" button while they're zoomed in, floating at the bottom right (`.reset-button`).
 *
 * Upstream: part of `state-history-charts` (src/components/chart/state-history-charts.ts)
 */
@Composable
private fun HistoryZoomReset(zoom: ChartZoomState, label: String, modifier: Modifier) {
    if (!zoom.zoom.isZoomed) return
    HAAccentButton(
        label,
        { zoom.zoom = ChartZoom() },
        modifier = modifier.padding(RESET_MARGIN).shadow(RESET_ELEVATION, CircleShape),
        size = ButtonSize.LARGE,
        prefix = {
            DashboardIcon("mdi:restart", LocalHAColorScheme.current.colorOnPrimaryLoud, Modifier.size(HASize.X2L))
        },
    )
}

/**
 * Below the filters: a spinner while it loads, a prompt to pick something, the error, or the charts.
 *
 * Upstream: part of `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
private fun HistoryContent(
    state: HistoryPanelState,
    hass: HassSnapshot,
    range: DateRange,
    now: Instant,
    interactions: CardInteractions,
    zoom: ChartZoomState,
) {
    when (state) {
        HistoryPanelState.Loading -> Box(Modifier.fillMaxWidth().padding(HADimens.SPACE4), Alignment.Center) {
            HALoading()
        }
        HistoryPanelState.NoTarget -> PanelMessage(hass.localize("ui.panel.history.start_search"))
        is HistoryPanelState.Failed -> PanelMessage(
            "${hass.localize("$CHARTS.error")}: ${LocalContext.current.loadErrorText(state.error)}",
            error = true,
        )
        is HistoryPanelState.Ready -> HistoryCharts(state.history, hass, range, now, interactions, zoom)
    }
}

/**
 * A message in place of the charts, centred as the frontend's `.start-search` and `.info`.
 *
 * Upstream: part of `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
internal fun PanelMessage(text: String, error: Boolean = false) {
    val colors = LocalHAColorScheme.current
    Text(
        text,
        style = HATextStyle.Body.copy(textAlign = TextAlign.Center),
        color = if (error) colors.colorOnDangerNormal else colors.colorTextSecondary,
        modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
    )
}

/**
 * "Remove all selections" while something is picked, and the menu with "Download data".
 *
 * Upstream: part of `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
private fun HistoryToolbar(
    toolbar: PanelToolbar,
    hass: HassSnapshot,
    state: HistoryPanelState,
    hasTarget: Boolean,
    onRemoveAll: () -> Unit,
    viewModel: HistoryPanelViewModel,
) {
    val loading = state == HistoryPanelState.Loading && hasTarget
    val history = (state as? HistoryPanelState.Ready)?.history
    var noData by remember { mutableStateOf(false) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        if (uri != null && history != null) viewModel.downloadCsv(history, uri)
    }
    val removeAll =
        PanelAction("mdi:filter-remove", hass.localize("$HISTORY.remove_all"), enabled = !loading, onRemoveAll)
    val download = PanelAction("mdi:download", hass.localize("$HISTORY.download_data"), enabled = !loading) {
        if (!hasTarget || history == null) noData = true else saveAs.launch(CSV_FILE)
    }
    toolbar.Actions(
        PanelActions(
            buttons = listOfNotNull(removeAll.takeIf { hasTarget }),
            menu = listOf(download),
            menuLabel = hass.localize("ui.common.menu"),
        ),
    )
    if (noData) {
        NoDataDialog(hass) { noData = false }
    }
    DownloadOutcome(hass, viewModel)
}

/**
 * Port of the alert `_downloadHistory` shows when there's nothing to download.
 *
 * Upstream: part of `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
private fun NoDataDialog(hass: HassSnapshot, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(hass.localize("$HISTORY.download_data_error"), style = HATextStyle.HeadlineMedium) },
        text = { Text(hass.localize("$HISTORY.error_no_data"), style = HATextStyle.Body) },
        confirmButton = { HAPlainButton(hass.localize("ui.common.ok"), onDismiss) },
    )
}

/**
 * That the CSV couldn't be saved ("Unable to download data").
 *
 * Upstream: part of `ha-panel-history` (src/panels/history/ha-panel-history.ts)
 */
@Composable
private fun DownloadOutcome(hass: HassSnapshot, viewModel: HistoryPanelViewModel) {
    val download by viewModel.download.collectAsStateWithLifecycle()
    val done = download as? HistoryDownload.Done ?: return
    if (!done.failed) {
        LaunchedEffect(done) { viewModel.onDownloadShown() }
        return
    }
    AlertDialog(
        onDismissRequest = viewModel::onDownloadShown,
        title = { Text(hass.localize("$HISTORY.download_data_error"), style = HATextStyle.HeadlineMedium) },
        confirmButton = { HAPlainButton(hass.localize("ui.common.ok"), viewModel::onDownloadShown) },
    )
}

/**
 * The history's charts, or that it has none.
 *
 * Upstream: part of `state-history-charts` (src/components/chart/state-history-charts.ts)
 */
@Composable
private fun HistoryCharts(
    history: HistoryResult,
    hass: HassSnapshot,
    range: DateRange,
    now: Instant,
    interactions: CardInteractions,
    zoom: ChartZoomState,
) {
    if (history.line.isEmpty() && history.timeline.isEmpty()) {
        PanelMessage(hass.localize("$CHARTS.no_history_found"))
        return
    }
    HistoryChartsList(history, hass, range, now, interactions, zoom)
}

private const val HISTORY = "ui.panel.history"
private const val CHARTS = "ui.components.history_charts"
private const val CSV = "text/csv"
private const val CSV_FILE = "history.csv"
private val RESET_MARGIN = 24.dp
private val RESET_ELEVATION = 6.dp
