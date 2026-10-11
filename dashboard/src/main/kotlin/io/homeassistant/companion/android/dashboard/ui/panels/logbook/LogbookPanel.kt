package io.homeassistant.companion.android.dashboard.ui.panels.logbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.logbook.LogbookRow
import io.homeassistant.companion.android.dashboard.logbook.causePhrase
import io.homeassistant.companion.android.dashboard.logbook.logbookCompatible
import io.homeassistant.companion.android.dashboard.logbook.logbookRows
import io.homeassistant.companion.android.dashboard.logbook.logbookUsers
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.panels.RangePreset
import io.homeassistant.companion.android.dashboard.panels.historyLogbookQuery
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.target.resolveEntityIds
import io.homeassistant.companion.android.dashboard.ui.components.DateRangeField
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.TargetPicker
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.panels.DateRangeSaver
import io.homeassistant.companion.android.dashboard.ui.panels.PanelAction
import io.homeassistant.companion.android.dashboard.ui.panels.PanelActions
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbar
import io.homeassistant.companion.android.dashboard.ui.panels.TargetSaver
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Port of `ha-panel-logbook` on a phone: the period (from the start of the last hour to the end of the next one at
 * first) and the entities to list (every entity's when none is picked), then their entries, newest first, each
 * naming what it is about and what caused it. [query] is the panel's address's ("entity_id=…&start_date=…"), as the
 * details' "Show more" opens it. Its refresh button goes in the [toolbar].
 *
 * Upstream: `ha-panel-logbook` (src/panels/logbook/ha-panel-logbook.ts)
 */
@Composable
internal fun LogbookPanel(
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    query: String?,
    toolbar: PanelToolbar,
    interactions: CardInteractions,
) {
    val snapshot = hass.value ?: return
    val current = now.value ?: return
    val refresh = rememberRefresh(toolbar, snapshot.localize("ui.common.refresh"))
    val initial = remember(query) { historyLogbookQuery(query) }
    var range by rememberSaveable(query, stateSaver = DateRangeSaver) {
        val around = DateRange.aroundNow(current)
        mutableStateOf(DateRange(initial.start ?: around.start, initial.end ?: around.end))
    }
    var target by rememberSaveable(query, stateSaver = TargetSaver) { mutableStateOf(initial.target ?: Target()) }
    val viewModel = hiltViewModel<LogbookViewModel>(key = "logbook-panel")
    // Opened without a target in the address, it lists what was picked last time, as the frontend does
    var restored by rememberSaveable(query) { mutableStateOf(initial.target != null) }
    LaunchedEffect(query) {
        if (!restored) viewModel.pickedTarget()?.let { target = it }
        restored = true
    }
    // Port of `_getEntityIds`: what the target stands for, every entity when that's nothing
    val entityIds = remember(target, snapshot.registries, snapshot.states.keys) { snapshot.resolveEntityIds(target) }
    val source = LogbookSource.Range(range.start, range.end, entityIds.takeIf { it.isNotEmpty() })
    val request = LogbookRequest(source, snapshot.user?.isAdmin == true, refresh)
    // Not before the picked entities are known, so it doesn't load every entity's first
    LaunchedEffect(request, restored) { if (restored) viewModel.show(request) }
    val logbook by viewModel.logbook.collectAsStateWithLifecycle()
    val instant = current.toInstant()
    val rows = remember(logbook, snapshot, current.toLocalDate()) {
        logbook.map { (entries, users, traces) ->
            snapshot.logbookRows(entries, snapshot.logbookUsers(users), traces, instant, named = true)
        }
    }
    var relative by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "filters") {
            Column(Modifier.padding(HADimens.SPACE4)) {
                DateRangeField(range, snapshot, current, RangePreset.BASIC, onChange = { range = it })
                TargetPicker(
                    target,
                    snapshot,
                    entityFilter = { snapshot.logbookCompatible(it) },
                    onChange = {
                        target = it
                        viewModel.savePickedTarget(it)
                    },
                    modifier = Modifier.padding(top = HADimens.SPACE4),
                )
            }
        }
        val actions = LogbookListActions(
            onOpenEntity = { interactions.onAction(CardAction.MoreInfo(it)) },
            onOpenTrace = { interactions.onAction(CardAction.Navigate(it, replace = false)) },
            onToggleTime = { relative = !relative },
        )
        logbookContent(rows, snapshot, instant, relative, actions)
    }
}

/**
 * The panel's refresh button, in the [toolbar]: how many times it was pressed, each loading the logbook again.
 *
 * Upstream: part of `ha-panel-logbook` (src/panels/logbook/ha-panel-logbook.ts)
 */
@Composable
private fun rememberRefresh(toolbar: PanelToolbar, label: String): Int {
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    toolbar.Actions(PanelActions(buttons = listOf(PanelAction(REFRESH_ICON, label) { refresh++ })))
    return refresh
}

/** The entries as items: the day headers and rows, else that it loads, that there are none, or why not. */
private fun LazyListScope.logbookContent(
    rows: Loadable<List<LogbookRow>>,
    hass: HassSnapshot,
    now: Instant,
    relative: Boolean,
    actions: LogbookListActions,
) {
    when (rows) {
        Loadable.Loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HALoading() }
        }
        is Loadable.Failed -> item(key = "error") {
            LogbookMessage(
                "${hass.localize("$LOGBOOK.retrieval_error")}: ${LocalContext.current.loadErrorText(rows.error)}",
                LocalHAColorScheme.current.colorOnDangerNormal,
            )
        }
        is Loadable.Ready -> if (rows.value.isEmpty()) {
            item(key = "empty") {
                LogbookMessage(
                    hass.localize("$LOGBOOK.entries_not_found"),
                    LocalHAColorScheme.current.colorTextSecondary,
                )
            }
        } else {
            val traceLabel = hass.localize("$LOGBOOK.view_trace")
            itemsIndexed(rows.value) { _, row ->
                Column(Modifier.padding(horizontal = HADimens.SPACE4)) {
                    row.dateHeader?.let { LogbookDayHeader(it, Modifier.padding(bottom = HADimens.SPACE2)) }
                    val at = Instant.ofEpochMilli(row.whenMillis)
                    val time = if (relative) hass.formats.relativeTime(at, now) else hass.formats.timeWithSeconds(at)
                    LogbookListRow(
                        row,
                        LogbookListTexts(
                            time,
                            row.cause?.let {
                                hass.causePhrase(it)
                            },
                            traceLabel,
                        ),
                        actions,
                    )
                }
            }
        }
    }
}

private const val LOGBOOK = "ui.components.logbook"
private const val REFRESH_ICON = "mdi:refresh"
