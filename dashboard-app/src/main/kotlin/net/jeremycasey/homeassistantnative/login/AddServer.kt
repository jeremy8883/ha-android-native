package net.jeremycasey.homeassistantnative.login

import io.homeassistant.companion.android.common.data.authentication.ServerRegistrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.flatMap
import io.homeassistant.companion.android.database.server.TemporaryServer
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl
import timber.log.Timber

/**
 * Adds the server logged in to with [LoginFlowApi] and makes it the active one: the login code becomes the session's
 * tokens, and the server's name and version are read, as the companion app does when it registers. This app doesn't
 * register as a mobile device (that's for notifications, sensors and location, which stay with the companion app), so
 * it reads them itself (the companion app's `NameYourDeviceViewModel.addServer` and `IntegrationRepository.registerDevice`).
 */
class AddServer @Inject constructor(
    private val registration: ServerRegistrationRepository,
    private val serverManager: ServerManager,
) {
    /** @return the new server's id, or why it couldn't be added */
    suspend operator fun invoke(server: HttpUrl, code: String): Fetched<Int> =
        exchange(server, code).flatMap { temporary -> store(serverManager.addServer(temporary)) }

    /** The login code exchanged for the session's tokens, as a server to add. */
    private suspend fun exchange(server: HttpUrl, code: String): Fetched<TemporaryServer> = try {
        // As the companion app: plain http stays allowed until the user decides (null), https never downgrades
        registration.registerAuthorizationCode(
            server.baseUrl(),
            code,
            allowInsecureConnection = if (server.isHttps) false else null,
        )?.let { Fetched.Success(it) } ?: Fetched.Failure(LoadError.UnexpectedResponse("auth/token"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Failed to exchange the login code")
        Fetched.Failure(LoadError.NoResponse)
    }

    /** Read the added server's name, version and user, and make it the active one; forget it when they can't be. */
    private suspend fun store(serverId: Int): Fetched<Int> {
        val webSocket = serverManager.webSocketRepository(serverId)
        val config = webSocket.getConfig()
        // Stores the user (name, admin) with the server
        val user = webSocket.getCurrentUser()
        if (config == null || user == null) {
            Timber.w("Failed to read the new server's config or user, removing it")
            forget(serverId)
            return Fetched.Failure(LoadError.NoResponse)
        }
        serverManager.getServer(serverId)?.let { added ->
            serverManager.updateServer(added.copy(_name = config.locationName, _version = config.version))
        }
        serverManager.activateServer(serverId)
        return Fetched.Success(serverId)
    }

    private suspend fun forget(serverId: Int) {
        try {
            serverManager.authenticationRepository(serverId).revokeSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to revoke the session of a server that couldn't be added")
        }
        serverManager.removeServer(serverId)
    }
}

/** The server's address without a path, as servers are stored: `http://homeassistant.local:8123`. */
internal fun HttpUrl.baseUrl(): String = newBuilder().encodedPath("/").query(null).fragment(null).build()
    .toString().removeSuffix("/")
