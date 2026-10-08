package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** The URL the server is reached at now, without a trailing slash, for resolving server paths. */
internal val LocalServerUrl = compositionLocalOf<String?> { null }

/**
 * An image from the server: [path] is resolved against [LocalServerUrl] like the frontend's `hassUrl`, full URLs
 * are used as they are. Loaded with the app's image loader, which uses its HTTP client (certificates included).
 */
@Composable
internal fun ServerImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = resolveServerUrl(path, LocalServerUrl.current) ?: return
    AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

/** Port of `hassUrl`: paths starting with `/` are on the server, anything else is already a URL. */
internal fun resolveServerUrl(path: String?, serverUrl: String?): String? = when {
    path.isNullOrEmpty() -> null
    path.startsWith("/") -> serverUrl?.let { it + path }
    else -> path
}
