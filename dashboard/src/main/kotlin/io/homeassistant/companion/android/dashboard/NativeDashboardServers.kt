package io.homeassistant.companion.android.dashboard

import io.homeassistant.companion.android.dashboard.data.ActiveServerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * The server the native dashboards show, the active one, for them and the web frontend hosted with them to follow
 * it together.
 */
@Singleton
class NativeDashboardServers @Inject constructor(
    private val repository: ActiveServerRepository,
    private val paths: NativeDashboardPaths,
) {
    // Activating a server doesn't change the servers, so the active one is read again on each activation
    private val activations = MutableStateFlow(0)

    /** The active server's id and how many servers there are, updated as servers change or another is activated. */
    val activeServer: Flow<ActiveServer> = combine(repository.activeServer(), activations) { (_, count), _ ->
        ActiveServer(repository.activeServerId(), count)
    }

    /** Make [serverId] the active server, which the native dashboards then show. */
    suspend fun activate(serverId: Int) {
        repository.activateServer(serverId)
        paths.reset()
        activations.update { it + 1 }
    }

    /**
     * @property serverId the active server, `null` when there is none
     * @property serverCount how many servers there are
     */
    data class ActiveServer(val serverId: Int?, val serverCount: Int)
}
