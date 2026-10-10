package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

/** The token the server's brands API (`/api/brands/...` images: integration logos, entity pictures) asks for. */
class BrandsRepository @Inject constructor(private val serverManager: ServerManager) {
    /** The brands API's current token (`brands/access_token`), or why there is none. */
    suspend fun token(): Fetched<String> {
        val webSocket = serverManager.webSocketRepositoryOrNull() ?: return Fetched.Failure(LoadError.NoServer)
        return webSocket.request(BRANDS_TOKEN).expect<JsonObject>().flatMap { result ->
            result.string("token")?.let { Fetched.Success(it) }
                ?: Fetched.Failure(LoadError.UnexpectedResponse(BRANDS_TOKEN))
        }
    }
}

private const val BRANDS_TOKEN = "brands/access_token"
