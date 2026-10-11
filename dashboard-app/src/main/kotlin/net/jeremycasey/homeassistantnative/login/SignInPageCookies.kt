package net.jeremycasey.homeassistantnative.login

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.CookieJarCookieManagerShim
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * The cookies of a sign-in page in front of a server (a proxy's, such as Cloudflare Access), shared with the app's
 * connection once a server was signed in to through its web page: the proxy lets requests through only with them.
 * Until then no cookies are kept at all, so starting the app doesn't load the WebView for them (the companion app's
 * `CookieJarCookieManagerShim` reads the WebView's cookie store).
 */
@Singleton
class SignInPageCookies @Inject constructor(@ApplicationContext private val context: Context) : CookieJar {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }
    private val webViewCookies = CookieJarCookieManagerShim()

    @Volatile
    private var used: Boolean? = null

    /** Keep and send the WebView's cookies from now on, as a server is signed in to through its web page. */
    fun use() {
        if (used == true) return
        preferences.edit { putBoolean(USED, true) }
        used = true
    }

    private fun isUsed(): Boolean = used ?: preferences.getBoolean(USED, false).also { used = it }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (isUsed()) webViewCookies.loadForRequest(url) else emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (isUsed()) webViewCookies.saveFromResponse(url, cookies)
    }

    private companion object {
        const val PREFERENCES = "sign_in_page_cookies"
        const val USED = "used"
    }
}
