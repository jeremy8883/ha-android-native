package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.parsePanels
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** Which server the native dashboards show, and its panels. */
class ActiveServerRepository @Inject constructor(private val serverManager: ServerManager) {
    /** The id of the active server, for links into the rest of the app. */
    suspend fun activeServerId(): Int? = serverManager.getServer()?.id

    /** The active server's id and how many servers there are, updated as servers change. */
    fun activeServer(): Flow<Pair<Int?, Int>> = serverManager.serversFlow.map { servers ->
        serverManager.getServer()?.id to servers.size
    }

    /** Make [serverId] the active server, which the native dashboards then show. */
    suspend fun activateServer(serverId: Int) = serverManager.activateServer(serverId)

    /** The panels, loaded once, for telling which paths are dashboards. */
    suspend fun panels(): Fetched<Map<String, PanelInfo>> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request("get_panels").expect<JsonObject>().map(::parsePanels)
    }
}
