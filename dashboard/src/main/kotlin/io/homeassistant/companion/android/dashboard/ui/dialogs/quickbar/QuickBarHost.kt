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
import io.homeassistant.companion.android.dashboard.keyboard.KeyPress
import io.homeassistant.companion.android.dashboard.keyboard.ShortcutCommand
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuHandler
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarAction
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSection
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSource
import io.homeassistant.companion.android.dashboard.ui.DashboardViewModel
import io.homeassistant.companion.android.dashboard.ui.LoadErrorScreen
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.TargetPickerViewModel
import io.homeassistant.companion.android.dashboard.ui.dialogs.shortcuts.ShortcutsDialog

/**
 * What the dashboard's toolbar actions and keyboard shortcuts do: Search opens the quick bar, drawn here while open,
 * as do the shortcuts' sections; the others open the dashboard shown in the frontend, so only Search is offered when
 * other pages can't be opened ([onOpenWeb] is `null`), and the quick bar then offers only what is handled here. The
 * keyboard shortcuts dialog's hint leads to [onOpenSettings].
 *
 * Upstream: part of `hui-root`, `quick-bar-mixin` (src/panels/lovelace/hui-root.ts; src/state/quick-bar-mixin.ts)
 */
@Composable
internal fun dashboardMenuActions(
    viewModel: DashboardViewModel,
    hass: State<HassSnapshot?>,
    onOpenWeb: ((String) -> Unit)?,
    onMoreInfo: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
): DashboardMenuHandler {
    // The quick bar's section when open ("" for all), as upstream's `mode`
    var quickBar by rememberSaveable { mutableStateOf<String?>(null) }
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    val nativeOnly = onOpenWeb == null
    val offered = if (nativeOnly) setOf(DashboardMenuAction.Search) else ALL_ACTIONS
    val onShortcut = rememberShortcutHandler(viewModel, hass, offered) { command ->
        when (command) {
            is ShortcutCommand.ShowQuickBar -> quickBar = command.section.key
            ShortcutCommand.ToggleQuickBar -> quickBar = if (quickBar == null) ALL_SECTIONS else null
            ShortcutCommand.ShowShortcuts -> shortcuts = true
            else -> Unit
        }
    }
    val quickBarOpen = quickBar != null
    UnhandledKeys { press -> onShortcut(press, quickBarOpen) }
    quickBar?.let { mode ->
        DashboardQuickBar(
            viewModel = viewModel,
            hass = hass,
            nativeOnly = nativeOnly,
            initialSection = QuickBarSection.entries.firstOrNull { it.key == mode },
            onDismiss = { quickBar = null },
            onMoreInfo = onMoreInfo,
            onShowShortcuts = { shortcuts = true },
            onKey = { press -> onShortcut(press, true) },
        )
    }
    if (shortcuts) {
        hass.value?.localize?.let { ShortcutsDialog(it, onOpenSettings, onDismiss = { shortcuts = false }) }
    }
    return DashboardMenuHandler(offered) { action ->
        if (action == DashboardMenuAction.Search) {
            quickBar = ALL_SECTIONS
        } else {
            viewModel.onOpenShownInWeb(flag = action.webParam)
        }
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
    nativeOnly: Boolean,
    initialSection: QuickBarSection?,
    onDismiss: () -> Unit,
    onMoreInfo: (String) -> Unit,
    onShowShortcuts: () -> Unit,
    onKey: (KeyPress) -> Boolean,
) {
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    val panels = when (val loaded = sidebar) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> loaded
        is Loadable.Ready -> Loadable.Ready(loaded.value.panels)
    }
    QuickBarHost(
        hass = hass,
        panels = panels,
        nativeOnly = nativeOnly,
        onRetry = viewModel::onRetry,
        onDismiss = onDismiss,
        onRun = { action, onDone -> viewModel.onAction(action.toCardAction()).invokeOnCompletion { onDone() } },
        initialSection = initialSection,
        onKey = onKey,
    ) { action ->
        when (action) {
            is QuickBarAction.MoreInfo -> onMoreInfo(action.entityId)
            is QuickBarAction.Navigate -> viewModel.onOpenPath(action.path)
            is QuickBarAction.CallService -> viewModel.onAction(action.toCardAction())
            QuickBarAction.ShowShortcuts -> onShowShortcuts()
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
    nativeOnly: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onRun: (QuickBarAction.CallService, onDone: () -> Unit) -> Unit,
    initialSection: QuickBarSection? = null,
    onKey: (KeyPress) -> Boolean = { false },
    onChoose: (QuickBarAction) -> Unit,
) {
    val entries: TargetPickerViewModel = hiltViewModel()
    val configEntryDomains by entries.configEntryDomains.collectAsStateWithLifecycle()
    val snapshot = hass.value ?: return
    when (panels) {
        Loadable.Loading -> Unit
        is Loadable.Failed -> FailedSheet(panels.error, onRetry, onDismiss)
        is Loadable.Ready -> {
            val source = remember(snapshot, panels.value, configEntryDomains, nativeOnly) {
                QuickBarSource(snapshot, panels.value, configEntryDomains, nativeOnly)
            }
            QuickBarSheet(source, onDismiss, onChoose, onRun, initialSection, onKey)
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

private fun QuickBarAction.CallService.toCardAction() =
    CardAction.CallService(domain, service, data = null, target = null)

/** The quick bar's mode showing every section. */
private const val ALL_SECTIONS = ""

private val ALL_ACTIONS = setOf(
    DashboardMenuAction.Search,
    DashboardMenuAction.Assist,
    DashboardMenuAction.EditDashboard,
)
