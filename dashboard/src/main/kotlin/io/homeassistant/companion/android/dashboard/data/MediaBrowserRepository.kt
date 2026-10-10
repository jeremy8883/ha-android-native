package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowseId
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

/**
 * Browsing a media player's media (`media_player/browse_media`) and what its thumbnails need besides the brands
 * token ([BrandsRepository]): the user's credentials for the server's own images, only where they can be sent
 * safely.
 */
class MediaBrowserRepository @Inject constructor(private val serverManager: ServerManager) {
    /** The page at [id] of [entityId]'s media, or why it couldn't be loaded. */
    suspend fun browse(entityId: String, id: MediaBrowseId): Fetched<JsonObject> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        val where = listOfNotNull(
            id.contentId?.let { "media_content_id" to it },
            id.contentType?.let { "media_content_type" to it },
        )
        return webSocket.request(BROWSE, mapOf("entity_id" to entityId) + where).expect<JsonObject>()
    }

    /** The `Authorization` header for [url] on the server, `null` when credentials can't safely go there. */
    suspend fun authorization(url: String): String? {
        val server = serverManager.getServer() ?: return null
        return if (serverManager.connectionStateProvider(server.id).canSafelySendCredentials(url)) {
            serverManager.authenticationRepository(server.id).buildBearerToken()
        } else {
            null
        }
    }
}

private const val BROWSE = "media_player/browse_media"
