package io.homeassistant.companion.android.nativedashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.NativeDashboardServers
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
