package net.jeremycasey.homeassistantnative.login

import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/**
 * The end of logging in, for [LoginViewModel]: signing in on the server's own sign-in page in a web view, and adding
 * the server once logged in (natively or there), which sends its id to [loggedIn].
 */
internal class LoginSession(
    private val state: MutableStateFlow<LoginUiState>,
    private val scope: CoroutineScope,
    private val addServer: AddServer,
    private val signInPageCookies: SignInPageCookies,
    private val loggedIn: SendChannel<Int>,
) {
    /** Sign in on the server's own sign-in page instead of the native form. */
    fun signInWithBrowser() {
        val server = when (val current = state.value) {
            is LoginUiState.ChooseServer -> serverAddress(current.address)
                ?: return state.update { current.copy(problem = LoadError.UnexpectedResponse(ADDRESS)) }
            is LoginUiState.ChooseProvider -> current.server
            is LoginUiState.Form -> current.server
            is LoginUiState.Aborted -> current.server
            is LoginUiState.SigningIn, is LoginUiState.WebSignIn -> null
        }
        server?.let(::startWebSignIn)
    }

    /** Sign in on [server]'s own sign-in page, keeping the cookies of any sign-in page in front of it. */
    fun startWebSignIn(server: HttpUrl) {
        signInPageCookies.use()
        state.value = LoginUiState.WebSignIn(server, authorizeUrl(server).toString())
    }

    /** The sign-in page ended at the app with [callback]. */
    fun onWebSignInEnded(callback: SignInCallback) {
        val signIn = state.value as? LoginUiState.WebSignIn ?: return
        when (callback) {
            is SignInCallback.Code -> scope.launch { finish(signIn.server, callback.code) }
            SignInCallback.NoCode -> state.value = signIn.copy(problem = WebSignInProblem.NotFinished)
        }
    }

    /** The sign-in page couldn't be used, for [problem]. */
    fun onWebSignInProblem(problem: WebSignInProblem) {
        state.update { (it as? LoginUiState.WebSignIn)?.copy(problem = problem) ?: it }
    }

    /** Load the sign-in page again. */
    fun onWebSignInRetry() {
        state.update { current ->
            (current as? LoginUiState.WebSignIn)?.let { it.copy(problem = null, attempt = it.attempt + 1) } ?: current
        }
    }

    /** Logged in with [code]: add the server, or go back to its address with why it couldn't be. */
    suspend fun finish(server: HttpUrl, code: String) {
        state.value = LoginUiState.SigningIn(server)
        when (val added = addServer(server, code)) {
            is Fetched.Success -> {
                // Ready for the next login, as the view model outlives the screen (the activity's)
                state.value = LoginUiState.ChooseServer()
                loggedIn.send(added.value)
            }
            is Fetched.Failure ->
                state.value = LoginUiState.ChooseServer(server.baseUrl(), problem = added.error, manual = true)
        }
    }

    private companion object {
        const val ADDRESS = "address"
    }
}
