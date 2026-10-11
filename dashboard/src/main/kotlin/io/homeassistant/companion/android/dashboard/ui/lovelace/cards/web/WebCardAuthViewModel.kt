package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.web

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Signs the web cards' frontend in, as the companion app's web view does (`getExternalAuth`): the active server's
 * access token and its expiry.
 */
@HiltViewModel
internal class WebCardAuthViewModel @Inject constructor(private val serverManager: ServerManager) : ViewModel() {
    /** The `externalAuthSetToken` payload (`{access_token, expires_in}`), `null` when it can't be had. */
    suspend fun externalAuth(forceRefresh: Boolean): String? = try {
        serverManager.authenticationRepository().retrieveExternalAuthentication(forceRefresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Couldn't sign the web card in")
        null
    }
}
