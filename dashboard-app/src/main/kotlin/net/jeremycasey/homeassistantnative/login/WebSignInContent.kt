package net.jeremycasey.homeassistantnative.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import net.jeremycasey.homeassistantnative.R
import timber.log.Timber

/**
 * The server's sign-in page in a web view, as the companion app's onboarding shows it: any sign-in page in front of
 * the server first (its cookies are kept for the app's connection), then Home Assistant's, ending at the app with the
 * code. Back goes back a page, then to the address; a page that can't load says why, with "Try again".
 *
 * Upstream: none (native: the server's sign-in page in a web view)
 */
@Composable
internal fun WebSignInContent(state: LoginUiState.WebSignIn, events: WebSignInEvents, modifier: Modifier = Modifier) {
    val loadingState = remember { mutableStateOf(true) }
    val loading by loadingState
    val eventsState = rememberUpdatedState(events)
    val currentEvents by eventsState
    var webView by remember { mutableStateOf<WebView?>(null) }
    val client = remember {
        SignInWebViewClient(
            onLoading = { loading -> loadingState.value = loading },
            onEnded = { eventsState.value.onEnded(it) },
            onProblem = { eventsState.value.onProblem(it) },
        )
    }
    BackHandler {
        val view = webView
        if (state.problem == null && view?.canGoBack() == true) view.goBack() else currentEvents.onBack()
    }
    val problem = state.problem
    if (problem != null) {
        WebSignInProblemContent(problem, events.onRetry, modifier)
        return
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    configureForSignIn()
                    webViewClient = client
                    webView = this
                }
            },
            update = { view ->
                // A new attempt loads the page again
                if (view.tag != state.attempt) {
                    view.tag = state.attempt
                    client.mainUrl = state.url
                    view.loadUrl(state.url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) HALoading(Modifier.align(Alignment.Center))
    }
    DisposableEffect(Unit) {
        onDispose {
            // The proxy's cookies are what lets the app's connection through it later
            CookieManager.getInstance().flush()
            webView?.destroy()
            webView = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForSignIn() {
    // Home Assistant's sign-in page, and those in front of it, are scripts
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString += " ${HomeAssistantApis.USER_AGENT_STRING}"
    // A proxy's sign-in may run on another host (Cloudflare Access's team domain), as the companion app allows
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/**
 * Follows the sign-in pages, ending at the redirect URI. Never loads a page whose certificate isn't trusted, as the
 * companion app (the handshake is cancelled), and keeps other apps' links out of the sign-in.
 */
private class SignInWebViewClient(
    private val onLoading: (Boolean) -> Unit,
    private val onEnded: (SignInCallback) -> Unit,
    private val onProblem: (WebSignInProblem) -> Unit,
) : WebViewClient() {
    /** The page being loaded: only its own failures stop the sign-in, not those of what it pulls in. */
    var mainUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        signInCallback(url)?.let { callback ->
            onEnded(callback)
            return true
        }
        val loads = loadsInSignIn(url)
        if (loads && request.isForMainFrame) mainUrl = url
        return !loads
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onLoading(true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onLoading(false)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame != true) return
        Timber.w("The sign-in page failed to load: ${error?.errorCode} ${error?.description}")
        onProblem(WebSignInProblem.PageFailed(error?.description?.toString()))
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // Cancels the handshake: a certificate that isn't trusted is never accepted
        super.onReceivedSslError(view, handler, error)
        Timber.w("The sign-in page's certificate isn't trusted (${error?.primaryError})")
        if (error?.url == mainUrl) onProblem(WebSignInProblem.Untrusted)
    }
}

/**
 * Why the sign-in page couldn't load, with "Try again".
 *
 * Upstream: none (native: the server's sign-in page in a web view)
 */
@Composable
private fun WebSignInProblemContent(problem: WebSignInProblem, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterVertically),
    ) {
        Text(
            when (problem) {
                is WebSignInProblem.PageFailed ->
                    problem.description
                        ?.let { stringResource(R.string.login_web_failed_reason, it) }
                        ?: stringResource(R.string.login_web_failed)
                WebSignInProblem.Untrusted -> stringResource(R.string.login_web_untrusted)
                WebSignInProblem.NotFinished -> stringResource(R.string.login_web_not_finished)
            },
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxButtonWidth),
        )
        HAAccentButton(
            stringResource(R.string.login_web_retry),
            onRetry,
            Modifier.widthIn(max = MaxButtonWidth).fillMaxWidth(),
        )
    }
}
