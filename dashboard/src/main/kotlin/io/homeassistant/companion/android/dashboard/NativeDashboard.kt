package io.homeassistant.companion.android.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * @param request what to show, applied once per request
 * @param onOpenWeb opens a path the native dashboards don't show (other panels, an entity's full more-info); `null`
 *   when they can't be opened, which leaves them out of the drawer and the more-info sheet
 * @param onShowAccount shows the app's account sheet (the profile and the servers to switch to), which calls back
 *   with the chosen server; `null` leaves the drawer's account entry opening the profile
 * @param onOpenSettings opens the app's own settings from the drawer, in place of the frontend's; `null` keeps those
 *
 * Upstream: none (native: the app's destination hosting the dashboards of the active server)
 */
@Composable
fun NativeDashboard(
    request: NativeDashboardRequest,
    onOpenWeb: ((String) -> Unit)?,
    onShowAccount: ((onServerSelected: (Int) -> Unit) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val host: NativeDashboardHostViewModel = hiltViewModel()
    val server by host.activeServer.collectAsStateWithLifecycle()
    val (serverId, _, serverName) = server ?: return
    // A view model per server, so switching servers starts from that server's data
    val viewModel: DashboardViewModel = hiltViewModel(key = "native-dashboard-$serverId")
    DashboardScreen(
        viewModel = viewModel,
        onOpenWeb = onOpenWeb,
        request = request,
        onOpenSettings = onOpenSettings,
        serverName = serverName,
        onOpenAccount = onShowAccount?.let { show -> { show { selected -> host.activate(selected) } } },
    )
}

/** The active server the native dashboards show, and switching it. */
@HiltViewModel
internal class NativeDashboardHostViewModel @Inject constructor(private val servers: NativeDashboardServers) :
    ViewModel() {
    val activeServer: StateFlow<NativeDashboardServers.ActiveServer?> = servers.activeServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun activate(serverId: Int) {
        viewModelScope.launch { servers.activate(serverId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
