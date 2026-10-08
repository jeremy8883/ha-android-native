package io.homeassistant.companion.android.nativedashboard

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.dashboard.NativeDashboard
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import io.homeassistant.companion.android.dashboard.NativeDashboardServers
import io.homeassistant.companion.android.dashboard.NativeDashboardWeb
import io.homeassistant.companion.android.frontend.FrontendViewModel
import io.homeassistant.companion.android.frontend.navigation.FrontendCallbacks
import io.homeassistant.companion.android.frontend.navigation.FrontendContent
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The active server the native dashboards and the web frontend hosted with them follow, and switching it. */
@HiltViewModel
internal class NativeDashboardShellViewModel @Inject constructor(private val servers: NativeDashboardServers) :
    ViewModel() {
    val activeServerId: StateFlow<Int?> = servers.activeServer.map { it.serverId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun activate(serverId: Int) {
        viewModelScope.launch { servers.activate(serverId) }
    }
}

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
    var request by rememberSaveable(stateSaver = RequestSaver) { mutableStateOf(route.toRequest()) }
    // The page the frontend was created for, null until a page is opened in it
    var webFirstPath by rememberSaveable { mutableStateOf<String?>(null) }
    var webServerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var webVisible by rememberSaveable { mutableStateOf(false) }
    // A page to open in the frontend once it has resumed
    var pendingPath by remember { mutableStateOf<String?>(null) }
    // The dashboard the frontend shows its more-info dialog over, which stays in the web until the dialog closes
    var moreInfoDashboard by rememberSaveable { mutableStateOf<String?>(null) }

    // A frontend for another server is of no use once the native dashboards show another one
    LaunchedEffect(activeServerId) {
        if (webFirstPath != null && activeServerId != webServerId) {
            webFirstPath = null
            webVisible = false
        }
    }
    // Registered first, so the frontend's own back (its history) goes first
    BackHandler(enabled = webVisible) { webVisible = false }

    val frontend = webFirstPath?.let { frontendViewModel(firstPath = it, serverId = webServerId ?: SERVER_ID_ACTIVE) }
    val openWeb = { path: String ->
        moreInfoDashboard = path.takeIf { MORE_INFO_PARAM in it.substringAfter('?', "") }?.let(::dashboardOf)
        if (frontend == null) {
            webFirstPath = path
            webServerId = activeServerId
        } else {
            pendingPath = path
        }
        webVisible = true
    }
    val frontendRoute by (frontend?.frontendRoute ?: NO_ROUTE).collectAsStateWithLifecycle()
    val showNative = { path: String ->
        moreInfoDashboard = null
        webVisible = false
        request = NativeDashboardRequest(path = path, id = request.id + 1)
    }
    // The frontend drops the parameter as it opens the dialog; from then on, it marks its URL once the dialog closes
    if (frontend != null) {
        val route = frontendRoute?.let(Uri::parse)
        val dialogOpen = moreInfoDashboard != null &&
            route?.pathSegments?.firstOrNull() == moreInfoDashboard &&
            route?.query?.contains(MORE_INFO_PARAM) != true &&
            route?.fragment == null
        LaunchedEffect(dialogOpen) { if (dialogOpen) frontend.markWhenMoreInfoCloses(MORE_INFO_CLOSED) }
        LaunchedEffect(route?.fragment) {
            val dashboard = moreInfoDashboard
            if (route?.fragment == MORE_INFO_CLOSED && dashboard != null) showNative("/$dashboard")
        }
    }

    NativeDashboard(
        request = request,
        onOpenWeb = openWeb,
        onShowServerSwitcher = onShowServerSwitcher,
        web = frontend?.let {
            NativeDashboardWeb(
                visible = webVisible,
                panel = frontendRoute?.let(::panelOf),
                onShowDashboard = { webVisible = false },
            ) {
                PausedWhileHidden(visible = webVisible) {
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    pendingPath?.let { path ->
                        LaunchedEffect(path) {
                            // Navigating a paused WebView could be lost, so wait for it to resume
                            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                            frontend.openPath(path)
                            pendingPath = null
                        }
                    }
                    NativeDashboardHandOff(frontend.frontendRoute) { path ->
                        if (webVisible && dashboardOf(path) != moreInfoDashboard) showNative(path)
                    }
                    FrontendContent(
                        viewModel = frontend,
                        callbacks = frontendCallbacks,
                        onShowServerSwitcher = { onShowServerSwitcher(viewModel::activate) },
                        // The drawer opens over the frontend
                        onShowNativeNavigation = {
                            request = NativeDashboardRequest(openDrawer = true, id = request.id + 1)
                        },
                    )
                }
            }
        },
    )
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

private val RequestSaver = listSaver<NativeDashboardRequest, Any?>(
    save = { listOf(it.path, it.openDrawer, it.moreInfoEntityId, it.id) },
    restore = { NativeDashboardRequest(it[0] as String?, it[1] as Boolean, it[2] as String?, it[3] as Int) },
)

/** The panel (`config`, `history`) of a frontend [url], as the drawer names it. */
private fun panelOf(url: String): String? = Uri.parse(url).pathSegments.firstOrNull()

/** The dashboard of an app [path] such as `/home/overview?more-info-entity-id=light.desk`: `home`. */
private fun dashboardOf(path: String): String = path.substringBefore('?').removePrefix("/").substringBefore('/')

private val NO_ROUTE = MutableStateFlow<String?>(null)

private const val MORE_INFO_PARAM = "more-info-entity-id="
private const val MORE_INFO_CLOSED = "native-more-info-closed"

private const val FRONTEND_VIEW_MODEL_KEY = "native-dashboard-frontend"
