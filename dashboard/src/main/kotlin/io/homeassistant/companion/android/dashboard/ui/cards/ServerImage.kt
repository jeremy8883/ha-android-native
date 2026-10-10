package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** The URL the server is reached at now, without a trailing slash, for resolving server paths. */
internal val LocalServerUrl = compositionLocalOf<String?> { null }

/** The brands API's token, which its images (`/api/brands/...`) need; `null` until known. */
internal val LocalBrandsToken = compositionLocalOf<String?> { null }

/**
 * An image from the server: [path] is resolved against [LocalServerUrl] like the frontend's `hassUrl`, full URLs
 * are used as they are, and the brands API's images get its token ([LocalBrandsToken]); nothing shows until it's
 * known. Loaded with the app's image loader, which uses its HTTP client (certificates included).
 */
@Composable
internal fun ServerImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = resolveServerUrl(path, LocalServerUrl.current, LocalBrandsToken.current) ?: return
    AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

/**
 * Port of `hassUrl`: paths starting with `/` are on the server, anything else is already a URL; the brands API's
 * paths are signed with [brandsToken], and `null` without it.
 */
internal fun resolveServerUrl(path: String?, serverUrl: String?, brandsToken: String? = null): String? = when {
    path.isNullOrEmpty() -> null
    path.startsWith(BRANDS_PATH) -> brandsToken?.let { token ->
        serverUrl?.let { "$it$path${if ('?' in path) '&' else '?'}token=$token" }
    }
    path.startsWith("/") -> serverUrl?.let { it + path }
    else -> path
}

private const val BRANDS_PATH = "/api/brands/"
