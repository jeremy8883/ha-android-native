package io.homeassistant.companion.android.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.DashboardRepository
import io.homeassistant.companion.android.dashboard.ui.DashboardScreen
import io.homeassistant.companion.android.dashboard.ui.DashboardViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The native dashboards with their navigation drawer, for the app to host as a destination. They show the active
 * server, and start over when another server is activated.
 *
 * @param path the dashboard (and view) to show first, such as `/dashboard-test/kitchen`; `null` for the default
 * @param openDrawer whether to open the navigation drawer, as when the web frontend's menu button was pressed
 * @param moreInfoEntityId an entity to show the more-info of, as a `more-info-entity-id` link asks
 * @param onOpenWeb opens a path the native dashboards don't show (Settings, other panels) in the web frontend
 * @param onShowServerSwitcher shows the app's server picker, which calls back with the chosen server; `null` hides
 *   the drawer's server entry
 */
@Composable
fun NativeDashboard(
    path: String?,
    openDrawer: Boolean,
    moreInfoEntityId: String?,
    onOpenWeb: (String) -> Unit,
    onShowServerSwitcher: ((onServerSelected: (Int) -> Unit) -> Unit)? = null,
) {
    val host: NativeDashboardHostViewModel = hiltViewModel()
    val server by host.activeServer.collectAsStateWithLifecycle()
    val (serverId, serverCount) = server ?: return
    // A view model per server, so switching servers starts from that server's data
    val viewModel: DashboardViewModel = hiltViewModel(key = "native-dashboard-$serverId")
    LaunchedEffect(viewModel, path) { if (path != null) viewModel.onOpenPath(path) }
    DashboardScreen(
        viewModel = viewModel,
        onOpenWeb = onOpenWeb,
        openDrawer = openDrawer,
        initialMoreInfo = moreInfoEntityId,
        onSwitchServer = onShowServerSwitcher?.takeIf { serverCount > 1 }?.let { show ->
            { show { selected -> host.activate(selected) } }
        },
    )
}

/** The active server the native dashboards show, and switching it. */
@HiltViewModel
internal class NativeDashboardHostViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val paths: NativeDashboardPaths,
) : ViewModel() {
    val activeServer: StateFlow<Pair<Int?, Int>?> = repository.activeServer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun activate(serverId: Int) {
        viewModelScope.launch {
            repository.activateServer(serverId)
            paths.reset()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
