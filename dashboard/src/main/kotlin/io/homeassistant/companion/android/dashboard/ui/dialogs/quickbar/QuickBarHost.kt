package io.homeassistant.companion.android.dashboard.ui.dialogs.quickbar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarAction
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSource
import io.homeassistant.companion.android.dashboard.ui.DashboardViewModel
import io.homeassistant.companion.android.dashboard.ui.LoadErrorScreen
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.TargetPickerViewModel

/**
 * What the dashboard's toolbar actions do, `null` when other pages can't be opened ([onOpenWeb] is `null`): Search
 * opens the quick bar, drawn here while open; the others open the dashboard shown in the frontend.
 *
 * Upstream: part of `hui-root` (src/panels/lovelace/hui-root.ts)
 */
@Composable
internal fun dashboardMenuActions(
    viewModel: DashboardViewModel,
    hass: State<HassSnapshot?>,
    onOpenWeb: ((String) -> Unit)?,
    onMoreInfo: (String) -> Unit,
): ((DashboardMenuAction) -> Unit)? {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) DashboardQuickBar(viewModel, hass, onDismiss = { open = false }, onMoreInfo = onMoreInfo)
    if (onOpenWeb == null) return null
    return { action ->
        if (action == DashboardMenuAction.Search) open = true else viewModel.onOpenShownInWeb(flag = action.webParam)
    }
}

/**
 * The quick bar over the dashboards of [viewModel]: an entity opens its details ([onMoreInfo]), a page opens as
 * links do (here when shown natively, else in the frontend), a command runs.
 *
 * Upstream: `_handleItemSelected` of `ha-quick-bar` (src/dialogs/quick-bar/ha-quick-bar.ts)
 */
@Composable
private fun DashboardQuickBar(
    viewModel: DashboardViewModel,
    hass: State<HassSnapshot?>,
    onDismiss: () -> Unit,
    onMoreInfo: (String) -> Unit,
) {
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    val panels = when (val loaded = sidebar) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> loaded
        is Loadable.Ready -> Loadable.Ready(loaded.value.panels)
    }
    QuickBarHost(hass, panels, viewModel::onRetry, onDismiss) { action ->
        when (action) {
            is QuickBarAction.MoreInfo -> onMoreInfo(action.entityId)
            is QuickBarAction.Navigate -> viewModel.onOpenPath(action.path)
            is QuickBarAction.CallService -> viewModel.onAction(
                CardAction.CallService(action.domain, action.service, data = null, target = null),
            )
        }
    }
}

/**
 * The quick bar over the dashboards, once the [panels] it lists are known (or why they couldn't be, with [onRetry]).
 * Integrations' names for devices come with the target picker's config entries.
 *
 * Upstream: `showQuickBar` as `hui-root` opens it (src/dialogs/quick-bar/show-dialog-quick-bar.ts;
 * src/panels/lovelace/hui-root.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickBarHost(
    hass: State<HassSnapshot?>,
    panels: Loadable<Map<String, PanelInfo>>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onChoose: (QuickBarAction) -> Unit,
) {
    val entries: TargetPickerViewModel = hiltViewModel()
    val configEntryDomains by entries.configEntryDomains.collectAsStateWithLifecycle()
    val snapshot = hass.value ?: return
    when (panels) {
        Loadable.Loading -> Unit
        is Loadable.Failed -> FailedSheet(panels.error, onRetry, onDismiss)
        is Loadable.Ready -> {
            val source = remember(snapshot, panels.value, configEntryDomains) {
                QuickBarSource(snapshot, panels.value, configEntryDomains)
            }
            QuickBarSheet(source, onDismiss, onChoose)
        }
    }
}

/**
 * Why the quick bar can't be shown, with a retry.
 *
 * Upstream: none (native: the panels are loaded separately)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailedSheet(error: LoadError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    HAModalBottomSheet(bottomSheetState = rememberHAModalBottomSheetState(), onDismissRequest = onDismiss) {
        LoadErrorScreen(error, onRetry)
    }
}
