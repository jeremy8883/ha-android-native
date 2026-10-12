package io.homeassistant.companion.android.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.navigation.userBadge
import io.homeassistant.companion.android.dashboard.ui.components.NavigationDrawerContent
import io.homeassistant.companion.android.dashboard.ui.components.UnsupportedMessage
import io.homeassistant.companion.android.dashboard.ui.dialogs.notifications.NotificationDrawerHost
import io.homeassistant.companion.android.dashboard.ui.dialogs.notifications.NotificationsViewModel
import io.homeassistant.companion.android.dashboard.ui.dialogs.notifications.hasNotifications
import io.homeassistant.companion.android.dashboard.ui.lovelace.DashboardTopBar
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.ProvideCardLocals
import io.homeassistant.companion.android.dashboard.ui.lovelace.views.DashboardView
import io.homeassistant.companion.android.dashboard.ui.lovelace.views.ViewFooter
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoSheet
import io.homeassistant.companion.android.dashboard.ui.panels.NativePanel
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbar
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The dashboards' screen: the dashboard or native panel shown, the navigation drawer, the messages and an entity's
 * details.
 *
 * Upstream: `home-assistant-main`, `ha-panel-lovelace` (src/layouts/home-assistant-main.ts;
 * src/panels/lovelace/ha-panel-lovelace.ts)
 */
@Composable
internal fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenWeb: ((String) -> Unit)?,
    request: NativeDashboardRequest = NativeDashboardRequest(),
    onSwitchServer: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    // Cards that only lead to other pages are left out when those can't be opened
    SideEffect { viewModel.otherPagesAvailable = onOpenWeb != null }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDashboard by viewModel.selectedDashboardUrlPath.collectAsStateWithLifecycle()
    // Passed down as State so that only the cards whose derived content changes recompose
    val hass = viewModel.hass.collectAsStateWithLifecycle()
    val now = viewModel.now.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val content = uiState as? DashboardUiState.Content
    // Startup ends when the page shows with its entities (from the cache when there is one), for Android's
    // startup measurements and the baseline profile
    ReportDrawnWhen { uiState !is DashboardUiState.Loading && hass.value != null }
    DashboardBackHandlers(
        showsBack = content?.showsBack == true,
        isDefaultDashboard = selectedDashboard == null,
        viewModel = viewModel,
    )
    val snackbar = remember { SnackbarHostState() }
    val interactions = rememberCardInteractions(viewModel)
    var moreInfo by rememberSaveable { mutableStateOf<String?>(null) }
    DashboardMessages(viewModel, snackbar, onOpenWeb, onMoreInfo = { moreInfo = it })
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ApplyRequest(request, viewModel::onOpenPath, onMoreInfo = { moreInfo = it }, drawerState = drawerState)
    val scope = rememberCoroutineScope()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    ProvideCardLocals(serverUrl) {
        DashboardLayers(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen || content?.showsBack != true,
            drawer = {
                DashboardDrawer(
                    drawerState = drawerState,
                    viewModel = viewModel,
                    selected = selectedDashboard,
                    onlyDashboards = onOpenWeb == null,
                    onSwitchServer = onSwitchServer,
                    onOpenSettings = onOpenSettings,
                )
            },
        ) {
            DashboardScreenContent(
                uiState = uiState,
                hass = hass,
                now = now,
                onSelectTab = viewModel::onSelectTab,
                interactions = interactions,
                onBack = { viewModel.onBack() },
                onOpenMenu = { scope.launch { drawerState.open() } },
                snackbar = snackbar,
                status = status,
                onRetry = viewModel::onRetry,
                onOpenElsewhere = onOpenWeb?.let { { viewModel.onOpenShownInWeb() } },
                onMenuAction = onOpenWeb?.let { { action -> viewModel.onOpenShownInWeb(flag = action.webParam) } },
                hasNotifications = hasNotifications(),
            )
        }
        NotificationDrawerHost(hass, now)
        moreInfo?.let { entityId ->
            val onShowFull = onOpenWeb?.let { { viewModel.onOpenShownInWeb(entityId).also { moreInfo = null } } }
            MoreInfoSheet(entityId, hass, now, interactions, onShowFull, onDismiss = { moreInfo = null }, snackbar)
        }
    }
}

/**
 * Shows the screen's one-off events and the failures of refreshing its data, which can be retried.
 *
 * Upstream: `notification-manager` (src/managers/notification-manager.ts)
 */
@Composable
private fun DashboardMessages(
    viewModel: DashboardViewModel,
    snackbar: SnackbarHostState,
    onOpenWeb: ((String) -> Unit)?,
    onMoreInfo: (String?) -> Unit,
) {
    RefreshErrorMessages(
        refreshErrors = remember(viewModel) {
            viewModel.status.map { it.refreshError.takeIf { _ -> !it.offline } }.distinctUntilChanged()
        },
        snackbar = snackbar,
        onRetry = viewModel::onRetry,
    )
    DashboardEffects(
        events = viewModel.events,
        snackbar = snackbar,
        onConfirmed = viewModel::onAction,
        onCodeEntered = viewModel::onCodeEntered,
        onMoreInfo = onMoreInfo,
        onOpenWeb = onOpenWeb,
    )
}

/**
 * The native dashboards ([native]) under the navigation drawer; back presses go to the drawer while it is open.
 *
 * Upstream: part of `home-assistant-main` (src/layouts/home-assistant-main.ts)
 */
@Composable
private fun DashboardLayers(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    drawer: @Composable () -> Unit,
    native: @Composable () -> Unit,
) {
    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = gesturesEnabled, drawerContent = drawer) {
        native()
    }
}

/**
 * The navigation drawer's content, closing as an entry is chosen. [selected] is the dashboard shown, `null` for the
 * default one.
 *
 * Upstream: `ha-sidebar` (src/components/ha-sidebar.ts)
 */
@Composable
private fun DashboardDrawer(
    drawerState: DrawerState,
    viewModel: DashboardViewModel,
    selected: String?,
    onlyDashboards: Boolean,
    onSwitchServer: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    // The screen's instance, which also shows the drawer
    val notifications: NotificationsViewModel = hiltViewModel()
    val notificationList by notifications.notifications.collectAsStateWithLifecycle()
    val hass = viewModel.hass.collectAsStateWithLifecycle()
    val user by remember(hass) { derivedStateOf { hass.value?.userBadge() } }
    val scope = rememberCoroutineScope()
    NavigationDrawerContent(
        drawerState = drawerState,
        sidebar = sidebar,
        selected = selected ?: sidebar.valueOrNull?.defaultPanel,
        onRetry = viewModel::onRetry,
        onlyDashboards = onlyDashboards,
        onSwitchServer = onSwitchServer?.let { switch -> closingDrawer(drawerState, scope, switch) },
        onOpenSettings = onOpenSettings?.let { open -> closingDrawer(drawerState, scope, open) },
        notificationCount = notificationList.valueOrNull?.size,
        onOpenNotifications = closingDrawer(drawerState, scope, notifications::openDrawer),
        user = user,
        onOpen = closingDrawer(drawerState, scope, viewModel::onOpenPath),
    )
}

/**
 * Back closes a subview or returns to the page that opened the dashboard with a `historyBack` link, and from another
 * dashboard returns to the default one before leaving, like other top-level destinations.
 *
 * Upstream: none (native: Android's back navigation)
 */
@Composable
private fun DashboardBackHandlers(showsBack: Boolean, isDefaultDashboard: Boolean, viewModel: DashboardViewModel) {
    BackHandler(enabled = showsBack) { viewModel.onBack() }
    BackHandler(enabled = !showsBack && !isDefaultDashboard) { viewModel.onSelectDashboard(null) }
}

/**
 * Apply [request] once: open its path, more-info or the drawer. Saved, so it isn't applied again when recreated.
 *
 * Upstream: none (native: what the app asked to open, such as from a shortcut)
 */
@Composable
private fun ApplyRequest(
    request: NativeDashboardRequest,
    onOpenPath: (String) -> Unit,
    onMoreInfo: (String) -> Unit,
    drawerState: DrawerState,
) {
    var appliedRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(request) {
        if (appliedRequest == request.id) return@LaunchedEffect
        appliedRequest = request.id
        request.path?.let(onOpenPath)
        request.moreInfoEntityId?.let(onMoreInfo)
        if (request.openDrawer) drawerState.open()
    }
}

/** [action], after starting to close the drawer. */
private fun closingDrawer(drawerState: DrawerState, scope: CoroutineScope, action: () -> Unit): () -> Unit = {
    scope.launch { drawerState.close() }
    action()
}

private fun <T> closingDrawer(drawerState: DrawerState, scope: CoroutineScope, action: (T) -> Unit): (T) -> Unit = {
    scope.launch { drawerState.close() }
    action(it)
}

/**
 * The screen under the drawer: the toolbar, then the dashboard, native panel or why there is none, with the messages at
 * the bottom.
 *
 * Upstream: `ha-panel-lovelace`, `partial-panel-resolver` (src/panels/lovelace/ha-panel-lovelace.ts;
 * src/layouts/partial-panel-resolver.ts)
 */
@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onSelectTab: (String) -> Unit,
    interactions: CardInteractions,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    status: DashboardStatus = DashboardStatus.CURRENT,
    onRetry: () -> Unit = {},
    onOpenElsewhere: (() -> Unit)? = null,
    onMenuAction: ((DashboardMenuAction) -> Unit)? = null,
    hasNotifications: Boolean = false,
) {
    val content = uiState as? DashboardUiState.Content
    val panel = uiState as? DashboardUiState.Panel
    // A panel's refresh button asks it to load again
    val panelToolbar = remember(panel?.name) { PanelToolbar() }
    Scaffold(
        // Over the content, so they never move it
        snackbarHost = {
            DashboardBottomBars(snackbar, status.offline, status.updatedAt.takeIf { content != null }, now)
        },
        // A view's footer (the energy date selection) stays at the bottom, below the messages
        bottomBar = { content?.view?.let { ViewFooter(it, hass, now, interactions) } },
        topBar = {
            DashboardTopBar(
                uiState,
                onBack,
                onOpenMenu,
                panelToolbar.actions,
                hass,
                onSelectTab,
                onMenuAction,
                hasNotifications,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HALoading()
                }
                DashboardUiState.NotFound -> Message(stringResource(R.string.native_dashboard_not_found))
                is DashboardUiState.Error -> LoadErrorScreen(uiState.error, onRetry)
                is DashboardUiState.UnsupportedStrategy -> UnsupportedMessage(
                    uiState.title?.let { stringResource(R.string.native_dashboard_unsupported_strategy, it) }
                        ?: stringResource(R.string.native_dashboard_unsupported_untitled),
                    onOpenElsewhere,
                )
                is DashboardUiState.Content -> DashboardView(uiState, hass, now, interactions)
                is DashboardUiState.Panel -> NativePanel(uiState, panelToolbar, hass, now, interactions)
            }
            // Offline, the offline bar says it; retries would flicker the bar
            RefreshIndicator(
                visible = status.refreshing && !status.offline,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * A short message in place of the content.
 *
 * Upstream: none (native: what the native dashboards can't show)
 */
@Composable
internal fun Message(text: String) {
    Text(text, style = Css.Text.Body, modifier = Modifier.padding(Css.Space.S4))
}

@Preview
@Composable
private fun DashboardScreenNotFoundPreview() {
    HAThemeForPreview {
        DashboardScreenContent(
            uiState = DashboardUiState.NotFound,
            hass = mutableStateOf(null),
            now = mutableStateOf(null),
            onSelectTab = {},
            interactions = CardInteractions.NONE,
            onBack = {},
        )
    }
}

/**
 * What the cards report, to [viewModel].
 *
 * Upstream: none (native: connects the cards to the view model)
 */
@Composable
private fun rememberCardInteractions(viewModel: DashboardViewModel) = remember(viewModel) {
    CardInteractions(viewModel::onGesture, viewModel::onAction, viewModel::onEnergyChange)
}
