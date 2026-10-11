package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.ui.components.LocalServerUrl
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.UnsupportedCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * A card the native dashboard can't draw (a custom card, or a built-in one not ported yet), drawn by the
 * frontend in a web view: signed in as the companion app's web view is, showing only the card at its own height.
 * Its details and pages open in the app. While it loads, a thin progress bar; if it can't, the placeholder.
 *
 * Upstream: `hui-card`, drawn by the frontend itself in a web view (src/panels/lovelace/cards/hui-card.ts)
 */
@Composable
internal fun WebCard(config: JsonObject, type: String, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    val serverUrl = LocalServerUrl.current ?: return UnsupportedCard(type, modifier)
    var state by remember(config) { mutableStateOf<WebCardState>(WebCardState.Loading) }
    when (state) {
        WebCardState.Failed -> return UnsupportedCard(type, modifier)
        WebCardState.Loading -> LinearProgressIndicator(modifier.fillMaxWidth())
        is WebCardState.Shown -> Unit
    }
    val auth = hiltViewModel<WebCardAuthViewModel>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val height = (state as? WebCardState.Shown)?.heightDp ?: 0
    val webView = remember(config, serverUrl) {
        WebView(context).also { view ->
            view.setup(serverUrl, config, WebCardHost(auth, scope, onAction) { state = it })
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    // Laid out (a pixel high) while it loads, so its page runs and measures the card
    Box(modifier.fillMaxWidth().height(maxOf(height, 1).dp)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().height(maxOf(height, 1).dp))
    }
}

/** A web card's progress: loading, shown at a height, or failed. */
private sealed interface WebCardState {
    data object Loading : WebCardState

    data class Shown(val heightDp: Int) : WebCardState

    data object Failed : WebCardState
}

/** What a web card's page reports to: its sign-in, the scope it runs in, the app's actions and its state. */
private class WebCardHost(
    val auth: WebCardAuthViewModel,
    val scope: CoroutineScope,
    val onAction: (CardAction) -> Unit,
    val onState: (WebCardState) -> Unit,
)

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setup(serverUrl: String, config: JsonObject, host: WebCardHost) {
    val main = Handler(Looper.getMainLooper())
    setBackgroundColor(Color.TRANSPARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    isVerticalScrollBarEnabled = false
    val origin = serverUrl.toUri()
    addJavascriptInterface(ExternalAppBridge(this, host.auth, host.scope, main), EXTERNAL_APP)
    addJavascriptInterface(CardBridge(main, host.onAction, host.onState), NATIVE_CARD)
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port) return false
            // Links out of the server open in the browser
            context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            val script = view.context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
            view.evaluateJavascript(script.replace("__CONFIG__", config.toString()), null)
        }
    }
    loadUrl("$serverUrl$PAGE?$EXTERNAL_AUTH=1")
}

/**
 * The frontend's `window.externalApp`: signs it in (`getExternalAuth`), and answers its `config/get` with an app
 * that has none of the companion app's extras.
 */
private class ExternalAppBridge(
    private val webView: WebView,
    private val auth: WebCardAuthViewModel,
    private val scope: CoroutineScope,
    private val main: Handler,
) {
    @JavascriptInterface
    fun getExternalAuth(payload: String) {
        val request = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        // As the companion app, only the frontend's own callback is answered
        if ((request["callback"] as? JsonPrimitive)?.content != AUTH_CALLBACK) return
        val force = (request["force"] as? JsonPrimitive)?.content == "true"
        scope.launch {
            val token = auth.externalAuth(force)
            val script = if (token != null) "$AUTH_CALLBACK(true, $token)" else "$AUTH_CALLBACK(false)"
            webView.evaluateJavascript(script, null)
        }
    }

    @JavascriptInterface
    fun revokeExternalAuth(payload: String) {
        // Signing out is the app's own
        Timber.d("Ignored revokeExternalAuth from a web card: $payload")
    }

    @JavascriptInterface
    fun externalBus(message: String) {
        val json = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull()
        val id = json?.takeIf {
            (it["type"] as? JsonPrimitive)?.content == CONFIG_GET
        }?.get("id")?.jsonPrimitive?.intOrNull
        id?.let { main.post { webView.evaluateJavascript("externalBus(${configResult(it)});", null) } }
    }

    private fun configResult(id: Int): String = """{"id":$id,"type":"result","success":true,"result":$APP_CONFIG}"""
}

/** `window.nativeCard`, which the card script tells its height, its requests and whether it loaded. */
private class CardBridge(
    private val main: Handler,
    private val onAction: (CardAction) -> Unit,
    private val onState: (WebCardState) -> Unit,
) {
    @JavascriptInterface
    fun height(px: Int) {
        main.post { onState(WebCardState.Shown(px)) }
    }

    @JavascriptInterface
    fun loaded() = Unit

    @JavascriptInterface
    fun failed(reason: String) {
        Timber.w("A web card couldn't load: $reason")
        main.post { onState(WebCardState.Failed) }
    }

    @JavascriptInterface
    fun moreInfo(entityId: String) {
        main.post { onAction(CardAction.MoreInfo(entityId)) }
    }

    @JavascriptInterface
    fun navigate(path: String) {
        main.post { onAction(CardAction.Navigate(path, replace = false)) }
    }
}

private const val EXTERNAL_APP = "externalApp"
private const val NATIVE_CARD = "nativeCard"
private const val AUTH_CALLBACK = "externalAuthSetToken"
private const val CONFIG_GET = "config/get"
private const val EXTERNAL_AUTH = "external_auth"
private const val SCRIPT_ASSET = "native_web_card.js"

/** The page loaded: a dashboard, so the frontend loads its cards' code. */
private const val PAGE = "/lovelace/0"

/** What the app tells the frontend it can do: none of the companion app's extras. */
private const val APP_CONFIG = """{"hasSettingsScreen":false,"canWriteTag":false,"hasExoPlayer":false,""" +
    """"canCommissionMatter":false,"canImportThreadCredentials":false,"hasAssist":false,"hasBarCodeScanner":0,""" +
    """"canSetupImprov":false,"downloadFileSupported":false,"hasEntityAddTo":false}"""
