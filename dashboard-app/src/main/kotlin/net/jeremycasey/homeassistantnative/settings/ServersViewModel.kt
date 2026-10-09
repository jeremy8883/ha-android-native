package net.jeremycasey.homeassistantnative.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.dashboard.NativeDashboardServers
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A server the app is logged in to, as the server list shows it (the companion app's `ServerChooserItem`).
 *
 * @property avatar the user's profile picture, `null` until loaded or when they have none (initials show instead)
 */
internal data class ServerItem(
    val id: Int,
    val name: String,
    val address: String,
    val userName: String?,
    val active: Boolean,
    val avatar: Bitmap? = null,
) {
    val initials: String = userName.orEmpty().ifBlank { name }.split(" ").filter { it.isNotBlank() }
        .take(MAX_INITIALS).joinToString(separator = "") { it.first().uppercase() }.ifBlank { "?" }
}

private const val MAX_INITIALS = 2

/**
 * The servers the app is logged in to: switching between them and logging out of one, as the companion app's server
 * settings do (`ServerSettingsPresenterImpl.deleteServer`: revoke the session, then remove the server).
 */
@HiltViewModel
internal class ServersViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val servers: NativeDashboardServers,
    private val avatars: ServerUserAvatarUseCase,
) : ViewModel() {

    /** The users' profile pictures by server, as they load. */
    private val loadedAvatars = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** The servers, `null` until read. */
    val items: StateFlow<List<ServerItem>?> = combine(
        serverManager.serversFlow.onEach(::loadAvatars),
        servers.activeServer,
        loadedAvatars,
    ) { list, active, pictures ->
        list.sortedBy { it.listOrder }.map { server ->
            ServerItem(
                id = server.id,
                name = server.friendlyName,
                address = server.connection.externalUrl,
                userName = server.user.name,
                active = server.id == active.serverId,
                avatar = pictures[server.id],
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private fun loadAvatars(list: List<Server>) {
        list.filter { it.id !in loadedAvatars.value }.forEach { server ->
            viewModelScope.launch {
                avatars.getUserAvatar(server.id)?.let { picture ->
                    loadedAvatars.update { it + (server.id to picture) }
                }
            }
        }
    }

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
