package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.homeassistant.companion.android.dashboard.moreinfo.ThumbnailSource
import io.homeassistant.companion.android.dashboard.moreinfo.thumbnailSource
import io.homeassistant.companion.android.dashboard.ui.cards.LocalServerUrl

/**
 * Port of `ha-media-browser-thumbnail`: an integration's logo from the server's brands API (signed with its token,
 * dark for a dark theme), the server's own images with the user's credentials, others as they are. Nothing shows
 * while what it needs is missing, so the item's icon stays.
 */
@Composable
internal fun MediaBrowserThumbnail(url: String, auth: ThumbnailAuth, modifier: Modifier, contentScale: ContentScale) {
    val context = LocalContext.current
    val server = LocalServerUrl.current
    val dark = isSystemInDarkTheme()
    val request = remember(url, auth, server, dark) {
        when (val source = thumbnailSource(url)) {
            is ThumbnailSource.Brand -> auth.brandsToken?.let { token ->
                server?.let {
                    "$it/api/brands/integration/${source.domain}/${if (dark) "dark_" else ""}icon.png?token=$token"
                }
            }?.let { ImageRequest.Builder(context).data(it).build() }
            is ThumbnailSource.Local -> auth.authorization?.let { authorization ->
                server?.let {
                    ImageRequest.Builder(context)
                        .data(it + source.path)
                        .httpHeaders(NetworkHeaders.Builder().add(AUTHORIZATION, authorization).build())
                        .build()
                }
            }
            is ThumbnailSource.Remote -> ImageRequest.Builder(context).data(source.url).build()
        }
    } ?: return
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = contentScale)
}

private const val AUTHORIZATION = "Authorization"
