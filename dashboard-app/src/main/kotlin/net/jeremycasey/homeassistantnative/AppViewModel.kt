package net.jeremycasey.homeassistantnative

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the app is logged in to a server, and where its pages are on the web. */
@HiltViewModel
internal class AppViewModel @Inject constructor(private val serverManager: ServerManager) : ViewModel() {

    /** Whether a server is logged in to (has a session); `null` until the servers are read. */
    val hasServer: StateFlow<Boolean?> = serverManager.serversFlow
        .map { servers -> servers.any { it.session.isComplete() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** [path] on the active server's web frontend, as it is reached now; `null` while there is no safe address. */
    suspend fun webUrl(path: String): Uri? {
        val url = (serverManager.connectionStateProvider().urlFlow().first() as? UrlState.HasUrl)?.url ?: return null
        return Uri.parse(url.toString().removeSuffix("/") + "/" + path.removePrefix("/"))
    }
}
