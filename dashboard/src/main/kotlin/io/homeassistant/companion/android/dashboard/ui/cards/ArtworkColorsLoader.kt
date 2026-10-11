package io.homeassistant.companion.android.dashboard.ui.cards

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.homeassistant.companion.android.dashboard.derive.ArtworkColors
import io.homeassistant.companion.android.dashboard.derive.artworkColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The colours of the artwork at [path] (a server path or a URL), `null` while they load, without artwork, or when
 * it can't be read (upstream then keeps the theme's colours too). Loaded with the app's image loader, scaled down
 * to a fifth like node-vibrant, then quantized off the main thread.
 *
 * Upstream: `_setColors` of `hui-media-control-card`, with `extractColors`
 * (src/panels/lovelace/cards/hui-media-control-card.ts; src/common/image/extract_color.ts)
 */
@Composable
internal fun rememberArtworkColors(path: String?): State<ArtworkColors?> {
    val context = LocalContext.current
    val reader = remember(context) { ArtworkColorsReader(context) }
    val url = resolveServerUrl(path, LocalServerUrl.current, LocalBrandsToken.current)
    return produceState<ArtworkColors?>(initialValue = null, url) {
        value = url?.let { reader.read(it) }
    }
}

/** Reads an artwork's colours, quantizing on [compute]. */
private class ArtworkColorsReader(private val context: Context, private val compute: CoroutineDispatcher) {
    constructor(context: Context) : this(context, Dispatchers.Default)

    suspend fun read(url: String): ArtworkColors? {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        return when (val result = SingletonImageLoader.get(context).execute(request)) {
            is SuccessResult -> withContext(compute) { colorsOf(result.image.toBitmap()) }
            is ErrorResult -> {
                Timber.w(result.throwable, "Couldn't load the media artwork for its colours")
                null
            }
        }
    }
}

private fun colorsOf(bitmap: Bitmap): ArtworkColors? {
    val width = maxOf(1, bitmap.width / QUALITY)
    val height = maxOf(1, bitmap.height / QUALITY)
    val scaled = if (width < bitmap.width) bitmap.scale(width, height) else bitmap
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return artworkColors(pixels)
}

/** node-vibrant's default `quality`: each side scaled down by this. */
private const val QUALITY = 5
