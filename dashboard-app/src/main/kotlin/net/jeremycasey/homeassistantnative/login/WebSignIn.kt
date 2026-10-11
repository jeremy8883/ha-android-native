package net.jeremycasey.homeassistantnative.login

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import okhttp3.HttpUrl

// Signing in through the server's own sign-in page, in a web view, as the companion app's onboarding does
// (app/src/main/kotlin/io/homeassistant/companion/android/onboarding/connection/ConnectionViewModel.kt): for servers
// behind a sign-in page of their own (Cloudflare Access, Authelia...), and any way of logging in the native form
// doesn't handle.

/**
 * The server's sign-in page (`/auth/authorize`), as the companion app's client, ending at its redirect URI with the
 * code the token exchange takes.
 */
internal fun authorizeUrl(server: HttpUrl): HttpUrl = server.newBuilder()
    .encodedPath("/")
    .query(null)
    .fragment(null)
    .addPathSegments(AUTHORIZE_PATH)
    .addQueryParameter("response_type", "code")
    .addQueryParameter("client_id", LoginFlowApi.CLIENT_ID)
    .addQueryParameter("redirect_uri", LoginFlowApi.REDIRECT_URI)
    .build()

/** What the sign-in web view tells the login, as it goes. */
internal data class WebSignInEvents(
    val onEnded: (SignInCallback) -> Unit,
    val onProblem: (WebSignInProblem) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

/** Where the sign-in page sent the web view, when it is the redirect URI. */
internal sealed interface SignInCallback {
    /** Signed in: [code] is exchanged for the session's tokens. */
    data class Code(val code: String) : SignInCallback

    /** The redirect URI without a code: the sign-in didn't finish. */
    data object NoCode : SignInCallback
}

/** [url] as the sign-in's end, or `null` for any other address (the web view loads it as usual). */
internal fun signInCallback(url: String): SignInCallback? {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        null
    }
    val expected = URI(LoginFlowApi.REDIRECT_URI)
    val isCallback = uri != null && uri.scheme.equals(expected.scheme, ignoreCase = true) && uri.host == expected.host
    return if (isCallback) {
        uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == CODE_PARAM }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
            ?.let { SignInCallback.Code(it) }
            ?: SignInCallback.NoCode
    } else {
        null
    }
}

/**
 * Whether an answer to the login API that isn't Home Assistant's JSON comes from a sign-in page in front of the server
 * (a proxy such as Cloudflare Access or Authelia): it redirected to another host, or refused without Home Assistant's
 * answer. Its page needs a browser, so the web sign-in takes over.
 *
 * @param requested the address asked for
 * @param answered the address that answered, after the redirects followed
 * @param status the answer's HTTP status
 * @param isJson whether the answer was JSON, as Home Assistant's always are
 */
internal fun isSignInPage(requested: HttpUrl, answered: HttpUrl, status: Int, isJson: Boolean): Boolean =
    !isJson && (answered.host != requested.host || status in REFUSED_STATUSES)

/** Whether [url] may load in the sign-in web view: web pages only, never other apps' links. */
internal fun loadsInSignIn(url: String): Boolean {
    val scheme = try {
        URI(url).scheme
    } catch (_: URISyntaxException) {
        null
    }
    return scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
}

private const val AUTHORIZE_PATH = "auth/authorize"
private const val CODE_PARAM = "code"
private val REFUSED_STATUSES = setOf(401, 403)
