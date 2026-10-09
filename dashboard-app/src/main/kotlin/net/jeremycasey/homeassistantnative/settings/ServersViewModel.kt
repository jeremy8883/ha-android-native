package net.jeremycasey.homeassistantnative.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.dashboard.NativeDashboardServers
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** A server the app is logged in to, as the server list shows it. */
internal data class ServerItem(
    val id: Int,
    val name: String,
    val address: String,
    val userName: String?,
    val active: Boolean,
)

/**
 * The servers the app is logged in to: switching between them and logging out of one, as the companion app's server
 * settings do (`ServerSettingsPresenterImpl.deleteServer`: revoke the session, then remove the server).
 */
@HiltViewModel
internal class ServersViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val servers: NativeDashboardServers,
) : ViewModel() {

    /** The servers, `null` until read. */
    val items: StateFlow<List<ServerItem>?> = combine(serverManager.serversFlow, servers.activeServer) { list, active ->
        list.sortedBy { it.listOrder }.map { server ->
            ServerItem(
                id = server.id,
                name = server.friendlyName,
                address = server.connection.externalUrl,
                userName = server.user.name,
                active = server.id == active.serverId,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    fun onActivate(serverId: Int) {
        viewModelScope.launch { servers.activate(serverId) }
    }

    /** Log out of [serverId] and forget it; another server becomes active, if there is one. */
    fun onLogOut(serverId: Int) {
        viewModelScope.launch {
            try {
                serverManager.authenticationRepository(serverId).revokeSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // As the companion app, the server is removed anyway: the session is then unused
                Timber.w(e, "Failed to revoke the session of server $serverId")
            }
            val wasActive = items.value?.firstOrNull { it.id == serverId }?.active == true
            serverManager.removeServer(serverId)
            if (wasActive) serverManager.servers().firstOrNull()?.let { servers.activate(it.id) }
        }
    }

    private companion object {
        val STOP_TIMEOUT = 5.seconds.inWholeMilliseconds
    }
}
