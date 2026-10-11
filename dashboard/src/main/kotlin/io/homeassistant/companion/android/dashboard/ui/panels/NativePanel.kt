package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.DashboardUiState
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.panels.history.HistoryPanel
import io.homeassistant.companion.android.dashboard.ui.panels.logbook.LogbookPanel
import io.homeassistant.companion.android.dashboard.ui.panels.todo.TodoPanel
import java.time.ZonedDateTime

/**
 * A panel drawn natively that isn't a dashboard (one of `NATIVE_PANELS`), with its buttons in the [toolbar].
 *
 * Upstream: `partial-panel-resolver` (src/layouts/partial-panel-resolver.ts)
 */
@Composable
internal fun NativePanel(
    panel: DashboardUiState.Panel,
    toolbar: PanelToolbar,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    when (panel.name) {
        TODO -> TodoPanel(hass, now, interactions)
        LOGBOOK -> LogbookPanel(hass, now, panel.query, toolbar, interactions)
        HISTORY -> HistoryPanel(hass, now, panel.query, toolbar, interactions)
    }
}

private const val TODO = "todo"
private const val LOGBOOK = "logbook"
private const val HISTORY = "history"
