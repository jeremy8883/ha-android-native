package net.jeremycasey.homeassistantnative.login

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WebSignInTest {

    @Test
    fun `Given a server when opening its sign-in page then it asks for a code for the companion app's client`() {
        val url = authorizeUrl("https://home.example.com:8123/lovelace/0?edit=1".toHttpUrl())

        assertEquals(
            "https://home.example.com:8123/auth/authorize?response_type=code&client_id=https%3A%2F%2Fhome-assistant.io%2Fandroid&redirect_uri=homeassistant%3A%2F%2Fauth-callback",
            url.toString(),
        )
    }

    @Test
    fun `Given the sign-in's end when reading it then it has the code, decoded`() {
        assertEquals(
            SignInCallback.Code("abc/123+=="),
            signInCallback("homeassistant://auth-callback?code=abc%2F123%2B%3D%3D&state=x"),
        )
    }

    @Test
    fun `Given the sign-in's end without a code when reading it then it didn't finish`() {
        assertEquals(SignInCallback.NoCode, signInCallback("homeassistant://auth-callback?error=access_denied"))
        assertEquals(SignInCallback.NoCode, signInCallback("homeassistant://auth-callback?code="))
    }

    @Test
    fun `Given another address when reading it as the sign-in's end then it isn't`() {
        assertNull(signInCallback("https://home.example.com/auth/authorize?code=abc"))
        assertNull(signInCallback("homeassistant://navigate/lovelace?code=abc"))
        assertNull(signInCallback("not a url"))
    }

    @ParameterizedTest
    @CsvSource(
        // Cloudflare Access and Authelia send the request to their own sign-in page
        "https://home.example.com/auth/providers, https://team.cloudflareaccess.com/cdn-cgi/access/login, 200, false, true",
        "https://home.example.com/auth/providers, https://auth.example.com/?rd=x, 200, false, true",
        // Refused without Home Assistant's answer
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 401, false, true",
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 403, false, true",
        // Home Assistant's own answers, and a server that isn't Home Assistant at all
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 200, true, false",
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 403, true, false",
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 200, false, false",
        "https://home.example.com/auth/providers, https://home.example.com/auth/providers, 404, false, false",
    )
    fun `Given an answer to the login API when checking it then a sign-in page in front of the server is told apart`(
        requested: String,
        answered: String,
        status: Int,
        isJson: Boolean,
        signInPage: Boolean,
    ) {
        assertEquals(signInPage, isSignInPage(requested.toHttpUrl(), answered.toHttpUrl(), status, isJson))
    }

    @Test
    fun `Given a link on the sign-in page when following it then only web pages load`() {
        assertTrue(loadsInSignIn("https://team.cloudflareaccess.com/login"))
        assertTrue(loadsInSignIn("http://homeassistant.local:8123/auth/authorize"))
        assertFalse(loadsInSignIn("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(loadsInSignIn("mailto:help@example.com"))
    }
}
