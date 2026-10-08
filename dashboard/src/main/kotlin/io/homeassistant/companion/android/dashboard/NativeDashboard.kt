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
 * @param onOpenWeb opens a path the native dashboards don't show (Settings, other panels) in the web frontend
 * @param onShowServerSwitcher shows the app's server picker, which calls back with the chosen server; `null` hides
 *   the drawer's server entry
 * @param web the web frontend shown in their place for other pages, under their drawer
 */
@Composable
fun NativeDashboard(
    request: NativeDashboardRequest,
    onOpenWeb: (String) -> Unit,
    onShowServerSwitcher: ((onServerSelected: (Int) -> Unit) -> Unit)? = null,
    web: NativeDashboardWeb? = null,
) {
    val host: NativeDashboardHostViewModel = hiltViewModel()
    val server by host.activeServer.collectAsStateWithLifecycle()
    val (serverId, serverCount) = server ?: return
    // A view model per server, so switching servers starts from that server's data
    val viewModel: DashboardViewModel = hiltViewModel(key = "native-dashboard-$serverId")
    DashboardScreen(
        viewModel = viewModel,
        onOpenWeb = onOpenWeb,
        request = request,
        web = web,
        onSwitchServer = onShowServerSwitcher?.takeIf { serverCount > 1 }?.let { show ->
            { show { selected -> host.activate(selected) } }
        },
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
