package net.jeremycasey.homeassistantnative.login

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The server at [input], typed as an address such as `homeassistant.local:8123` or a full URL; plain http when no
 * scheme is given, as a local server usually is. `null` when it isn't an address.
 */
internal fun serverAddress(input: String): HttpUrl? {
    val text = input.trim()
    if (text.isEmpty()) return null
    val withScheme = if ("://" in text) text else "http://$text"
    return withScheme.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.fragment(null)?.build()
}
