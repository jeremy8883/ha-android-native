package io.homeassistant.companion.android.nativedashboard

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.dashboard.NativeDashboard
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import io.homeassistant.companion.android.dashboard.NativeDashboardWeb
import io.homeassistant.companion.android.frontend.FrontendViewModel
import io.homeassistant.companion.android.frontend.navigation.FrontendCallbacks
import io.homeassistant.companion.android.frontend.navigation.FrontendContent
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * The native dashboards with the web frontend for the pages they don't show, in one destination so the frontend's
 * WebView is kept between visits: it is created on the first page opened in it, never before, and kept loaded but
 * paused while the native dashboards show, so later pages open in it without loading the frontend again. The native
 * drawer opens over both.
 *
 * @param route what the native dashboards show first
 * @param frontendCallbacks what the web frontend asks of the app
 * @param onShowServerSwitcher shows the app's server picker, which calls back with the chosen server
 */
@Composable
internal fun NativeDashboardShell(
    route: NativeDashboardRoute,
    frontendCallbacks: FrontendCallbacks,
    onShowServerSwitcher: (onServerSelected: (Int) -> Unit) -> Unit,
) {
    val viewModel: NativeDashboardShellViewModel = hiltViewModel()
    val activeServerId by viewModel.activeServerId.collectAsStateWithLifecycle()
    val state = rememberSaveable(saver = ShellState.Saver) { ShellState(route.toRequest()) }

    // A frontend for another server is of no use once the native dashboards show another one
    LaunchedEffect(activeServerId) { state.forgetWebUnlessFor(activeServerId) }
    // Registered first, so the frontend's own back (its history) goes first
    BackHandler(enabled = state.webVisible) { state.webVisible = false }

    val frontend = state.webFirstPath?.let {
        frontendViewModel(firstPath = it, serverId = state.webServerId ?: SERVER_ID_ACTIVE)
    }
    val frontendRoute by (frontend?.frontendRoute ?: NO_ROUTE).collectAsStateWithLifecycle()
    if (frontend != null) MoreInfoCloseEffects(frontend, frontendRoute, state)

    NativeDashboard(
        request = state.request,
        onOpenWeb = { path -> state.openWeb(path, frontendCreated = frontend != null, activeServerId) },
        onShowServerSwitcher = onShowServerSwitcher,
        web = frontend?.let {
            NativeDashboardWeb(
                visible = state.webVisible,
                panel = frontendRoute?.let(::panelOf),
                onShowDashboard = { state.webVisible = false },
            ) {
                WebLayer(frontend, state, frontendCallbacks) { onShowServerSwitcher(viewModel::activate) }
            }
        },
    )
}

/**
 * The web frontend in the shell: paused while hidden, opening pages once resumed, and handing dashboards over to the
 * native ones.
 */
@Composable
private fun WebLayer(
    frontend: FrontendViewModel,
    state: ShellState,
    frontendCallbacks: FrontendCallbacks,
    onShowServerSwitcher: () -> Unit,
) {
    PausedWhileHidden(visible = state.webVisible) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        state.pendingPath?.let { path ->
            LaunchedEffect(path) {
                // Navigating a paused WebView could be lost, so wait for it to resume
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                frontend.openPath(path)
                state.pendingPath = null
            }
        }
        NativeDashboardHandOff(frontend.frontendRoute) { path ->
            if (state.webVisible && dashboardOf(path) != state.moreInfoDashboard) state.showNative(path)
        }
        FrontendContent(
            viewModel = frontend,
            callbacks = frontendCallbacks,
            onShowServerSwitcher = onShowServerSwitcher,
            // The drawer opens over the frontend
            onShowNativeNavigation = state::showDrawer,
        )
    }
}

/**
 * The frontend drops the more-info parameter as it opens the dialog; from then on, it marks its URL once the dialog
 * closes, which shows the native dashboard again.
 */
@Composable
private fun MoreInfoCloseEffects(frontend: FrontendViewModel, frontendRoute: String?, state: ShellState) {
    val uri = frontendRoute?.let(Uri::parse)
    val dialogOpen = state.moreInfoDashboard != null &&
        uri?.pathSegments?.firstOrNull() == state.moreInfoDashboard &&
        uri?.query?.contains(MORE_INFO_PARAM) != true &&
        uri?.fragment == null
    LaunchedEffect(dialogOpen) { if (dialogOpen) frontend.markWhenMoreInfoCloses(MORE_INFO_CLOSED) }
    LaunchedEffect(uri?.fragment) {
        val dashboard = state.moreInfoDashboard
        if (uri?.fragment == MORE_INFO_CLOSED && dashboard != null) state.showNative("/$dashboard")
    }
}

/**
 * What the shell shows: the native dashboards' [request], and the web frontend's first page (it is created for it),
 * server, visibility, the page to open in it next, and the dashboard its more-info dialog shows over.
 */
@Stable
private class ShellState(
    request: NativeDashboardRequest,
    webFirstPath: String? = null,
    webServerId: Int? = null,
    webVisible: Boolean = false,
    moreInfoDashboard: String? = null,
) {
    var request by mutableStateOf(request)
        private set

    /** The page the frontend was created for, `null` until a page is opened in it. */
    var webFirstPath by mutableStateOf(webFirstPath)
        private set
    var webServerId by mutableStateOf(webServerId)
        private set
    var webVisible by mutableStateOf(webVisible)

    /** A page to open in the frontend once it has resumed. */
    var pendingPath by mutableStateOf<String?>(null)

    /** The dashboard the frontend shows its more-info dialog over, which stays in the web until the dialog closes. */
    var moreInfoDashboard by mutableStateOf(moreInfoDashboard)
        private set

    /** Show [path] in the web frontend, creating it unless [frontendCreated]. */
    fun openWeb(path: String, frontendCreated: Boolean, activeServerId: Int?) {
        moreInfoDashboard = path.takeIf { MORE_INFO_PARAM in it.substringAfter('?', "") }?.let(::dashboardOf)
        if (frontendCreated) {
            pendingPath = path
        } else {
            webFirstPath = path
            webServerId = activeServerId
        }
        webVisible = true
    }

    /** Show the native dashboards at [path]. */
    fun showNative(path: String) {
        moreInfoDashboard = null
        webVisible = false
        request = NativeDashboardRequest(path = path, id = request.id + 1)
    }

    /** Open the native drawer, over whatever shows. */
    fun showDrawer() {
        request = NativeDashboardRequest(openDrawer = true, id = request.id + 1)
    }

    /** Drop the frontend when it was created for another server than [activeServerId]. */
    fun forgetWebUnlessFor(activeServerId: Int?) {
        if (webFirstPath != null && activeServerId != webServerId) {
            webFirstPath = null
            webVisible = false
        }
    }

    companion object {
        val Saver = listSaver<ShellState, Any?>(
            save = {
                with(it.request) { listOf(path, openDrawer, moreInfoEntityId, id) } +
                    listOf(it.webFirstPath, it.webServerId, it.webVisible, it.moreInfoDashboard)
            },
            restore = {
                ShellState(
                    request = NativeDashboardRequest(
                        it[0] as String?,
                        it[1] as Boolean,
                        it[2] as String?,
                        it[3] as Int,
                    ),
                    webFirstPath = it[4] as String?,
                    webServerId = it[5] as Int?,
                    webVisible = it[6] as Boolean,
                    moreInfoDashboard = it[7] as String?,
                )
            },
        )
    }
}

/**
 * The frontend's view model, created in this destination for [firstPath] as the frontend destination creates it for
 * its route. One per server, as the frontend shows one.
 */
@Composable
private fun frontendViewModel(firstPath: String, serverId: Int): FrontendViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner for the frontend" }
    val withRoute = remember(owner, firstPath) {
        RouteArgumentsOwner(owner, FrontendRoute(FrontendTarget.Path(firstPath)).toArguments())
    }
    return hiltViewModel(viewModelStoreOwner = withRoute, key = "$FRONTEND_VIEW_MODEL_KEY-$serverId")
}

/** [owner] (sharing its view models) with [arguments] given to the view models it creates, as a route's are. */
private class RouteArgumentsOwner(private val owner: ViewModelStoreOwner, private val arguments: Bundle) :
    ViewModelStoreOwner by owner,
    HasDefaultViewModelProviderFactory {
    private val defaults = owner as HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = defaults.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(defaults.defaultViewModelCreationExtras).apply {
            set(DEFAULT_ARGS_KEY, arguments)
        }
}

private fun NativeDashboardRoute.toRequest() =
    NativeDashboardRequest(path = path, openDrawer = openDrawer, moreInfoEntityId = moreInfoEntityId)

/** The panel (`config`, `history`) of a frontend [url], as the drawer names it. */
private fun panelOf(url: String): String? = Uri.parse(url).pathSegments.firstOrNull()

/** The dashboard of an app [path] such as `/home/overview?more-info-entity-id=light.desk`: `home`. */
private fun dashboardOf(path: String): String = path.substringBefore('?').removePrefix("/").substringBefore('/')

private val NO_ROUTE = MutableStateFlow<String?>(null)

private const val MORE_INFO_PARAM = "more-info-entity-id="
private const val MORE_INFO_CLOSED = "native-more-info-closed"

private const val FRONTEND_VIEW_MODEL_KEY = "native-dashboard-frontend"
