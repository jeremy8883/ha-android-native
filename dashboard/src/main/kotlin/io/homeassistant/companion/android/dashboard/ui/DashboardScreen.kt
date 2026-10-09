package io.homeassistant.companion.android.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import io.homeassistant.companion.android.dashboard.NativeDashboardWeb
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.LocalServerUrl
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenWeb: ((String) -> Unit)? = null,
    request: NativeDashboardRequest = NativeDashboardRequest(),
    onSwitchServer: (() -> Unit)? = null,
    web: NativeDashboardWeb? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDashboard by viewModel.selectedDashboardUrlPath.collectAsStateWithLifecycle()
    // Passed down as State so that only the cards whose derived content changes recompose
    val hass = viewModel.hass.collectAsStateWithLifecycle()
    val now = viewModel.now.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val content = uiState as? DashboardUiState.Content
    val webVisible = web?.visible == true
    DashboardBackHandlers(
        enabled = !webVisible,
        isSubview = content?.isSubview == true,
        isDefaultDashboard = selectedDashboard == null,
        viewModel = viewModel,
    )
    val snackbar = remember { SnackbarHostState() }
    val interactions = remember(viewModel) { CardInteractions(viewModel::onGesture, viewModel::onAction) }
    var moreInfo by rememberSaveable { mutableStateOf<String?>(null) }
    DashboardMessages(viewModel, snackbar, onOpenWeb, web, onMoreInfo = { moreInfo = it })
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ApplyRequest(request, viewModel::onOpenPath, onMoreInfo = { moreInfo = it }, drawerState = drawerState)
    val scope = rememberCoroutineScope()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalServerUrl provides serverUrl) {
        DashboardLayers(
            drawerState = drawerState,
            // The frontend has its own gestures, so over it the drawer only opens from its menu button
            gesturesEnabled = drawerState.isOpen || (!webVisible && content?.isSubview != true),
            drawer = {
                DashboardDrawer(
                    drawerState = drawerState,
                    viewModel = viewModel,
                    selected = if (web?.visible == true) web.panel else selectedDashboard,
                    selectsDefault = !webVisible,
                    onSwitchServer = onSwitchServer,
                )
            },
            web = web,
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
            )
        }
        moreInfo?.let { entityId ->
            val onShowFull = {
                moreInfo = null
                viewModel.onShowFullMoreInfo(entityId)
            }
            MoreInfoSheet(entityId, hass, now, interactions, onShowFull, onDismiss = { moreInfo = null }, snackbar)
        }
    }
}

/** Shows the screen's one-off events and the failures of refreshing its data, which can be retried. */
@Composable
private fun DashboardMessages(
    viewModel: DashboardViewModel,
    snackbar: SnackbarHostState,
    onOpenWeb: ((String) -> Unit)?,
    web: NativeDashboardWeb?,
    onMoreInfo: (String) -> Unit,
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
        onOpenWeb = onOpenWeb ?: viewModel::onOpenWebViaDeepLink,
        onShowDashboard = { web?.onShowDashboard?.invoke() },
    )
}

/**
 * The native dashboards ([native]) and the web frontend ([web]) in the same place, under the navigation drawer; back
 * presses go to the drawer while it is open.
 */
@Composable
private fun DashboardLayers(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    drawer: @Composable () -> Unit,
    web: NativeDashboardWeb?,
    native: @Composable () -> Unit,
) {
    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = gesturesEnabled, drawerContent = drawer) {
        val drawerClosed = drawerState.isClosed
        ScreenLayer(visible = web?.visible != true, backEnabled = drawerClosed, content = native)
        web?.let { ScreenLayer(visible = it.visible, backEnabled = drawerClosed, content = it.content) }
    }
}

/**
 * The navigation drawer's content, closing as an entry is chosen. [selected] is the panel shown; when `null`, the
 * default dashboard if [selectsDefault].
 */
@Composable
private fun DashboardDrawer(
    drawerState: DrawerState,
    viewModel: DashboardViewModel,
    selected: String?,
    selectsDefault: Boolean,
    onSwitchServer: (() -> Unit)?,
) {
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    NavigationDrawerContent(
        drawerState = drawerState,
        sidebar = sidebar,
        selected = selected ?: sidebar.valueOrNull?.defaultPanel?.takeIf { selectsDefault },
        onRetry = viewModel::onRetry,
        onSwitchServer = onSwitchServer?.let { switch -> closingDrawer(drawerState, scope, switch) },
        onOpen = closingDrawer(drawerState, scope, viewModel::onOpenPath),
    )
}

/**
 * Back closes a subview, and from another dashboard returns to the default one before leaving, like other top-level
 * destinations; [enabled] while the dashboards show.
 */
@Composable
private fun DashboardBackHandlers(
    enabled: Boolean,
    isSubview: Boolean,
    isDefaultDashboard: Boolean,
    viewModel: DashboardViewModel,
) {
    BackHandler(enabled = enabled && isSubview) { viewModel.onBack() }
    BackHandler(enabled = enabled && !isSubview && !isDefaultDashboard) { viewModel.onSelectDashboard(null) }
}

/** Apply [request] once: open its path, more-info or the drawer. Saved, so it isn't applied again when recreated. */
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
) {
    val content = uiState as? DashboardUiState.Content
    Scaffold(
        // Over the content, so they never move it
        snackbarHost = {
            DashboardBottomBars(snackbar, status.offline, status.updatedAt.takeIf { content != null }, now)
        },
        topBar = {
            val title = @Composable {
                Text(
                    when {
                        content?.isSubview == true -> content.subviewTitle.orEmpty()
                        else -> content?.title ?: stringResource(R.string.native_dashboard_title)
                    },
                )
            }
            if (content?.isSubview == true) {
                HATopBar(title = title, onBackClick = onBack)
            } else {
                HATopBar(
                    title = title,
                    navigationIcon = {
                        val menuLabel = stringResource(R.string.native_dashboard_menu)
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier.semantics {
                                contentDescription = menuLabel
                            },
                        ) {
                            DashboardIcon(name = MENU_ICON, tint = LocalHAColorScheme.current.colorTextPrimary)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HALoading()
                }
                DashboardUiState.NotFound -> Message(stringResource(R.string.native_dashboard_not_found))
                is DashboardUiState.Error -> LoadErrorScreen(uiState.error, onRetry)
                is DashboardUiState.UnsupportedStrategy -> Message(
                    stringResource(R.string.native_dashboard_unsupported_strategy, uiState.type.orEmpty()),
                )
                is DashboardUiState.Content -> DashboardView(uiState, hass, now, onSelectTab, interactions)
            }
            // Offline, the offline bar says it; retries would flicker the bar
            RefreshIndicator(
                visible = status.refreshing && !status.offline,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(text, style = HATextStyle.Body, modifier = Modifier.padding(HADimens.SPACE4))
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

private const val MENU_ICON = "mdi:menu"
