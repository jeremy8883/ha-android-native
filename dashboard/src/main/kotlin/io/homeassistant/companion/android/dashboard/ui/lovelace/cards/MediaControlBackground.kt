package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.homeassistant.companion.android.dashboard.derive.ArtworkColors
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.MediaControlModel
import io.homeassistant.companion.android.dashboard.ui.components.LocalBrandsToken
import io.homeassistant.companion.android.dashboard.ui.components.LocalServerUrl
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.components.resolveServerUrl
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The colour block, the speaker picture of a player without artwork, and the artwork fading into the colour; grey
 * while off.
 *
 * Upstream: part of `hui-media-control-card` (`.background`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
internal fun MediaBackground(model: MediaControlModel, artwork: ArtworkColors?, height: Dp, modifier: Modifier) {
    val dimmed = model.off || model.unavailable
    val primary = DisplayColor.Theme(PRIMARY).toColor() ?: Color.Gray
    val background = (artwork?.background?.let(::Color) ?: primary).let { if (dimmed) it.grayscale() else it }
    val grayFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }).takeIf { dimmed }
    val hasImage = model.picture != null
    Box(modifier.background(background)) {
        // `.no-img`: hidden while unavailable, and behind the artwork while it shows
        if (!model.unavailable && (model.off || !hasImage)) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(HALF).fillMaxHeight(), Alignment.Center) {
                FilteredServerImage(
                    MEDIA_PLAYER_BACKGROUND,
                    Modifier.size(BACKGROUND_WIDTH, BACKGROUND_HEIGHT),
                    grayFilter,
                )
            }
        }
        if (hasImage && !dimmed) {
            ServerImage(
                model.picture,
                contentDescription = model.title,
                modifier = Modifier.align(Alignment.CenterEnd).width(height).fillMaxHeight(),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(height)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(background, background.copy(alpha = 0f)))),
            )
        }
    }
}

/**
 * An image from the server (see [ServerImage]) at its size within [modifier], drawn through [colorFilter].
 *
 * Upstream: part of `hui-media-control-card` (`.no-img`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun FilteredServerImage(path: String, modifier: Modifier, colorFilter: ColorFilter?) {
    val url = resolveServerUrl(path, LocalServerUrl.current, LocalBrandsToken.current) ?: return
    AsyncImage(url, null, modifier, contentScale = ContentScale.Fit, colorFilter = colorFilter)
}

/** CSS `grayscale(1)`: this colour's luminance as grey. */
private fun Color.grayscale(): Color {
    val luminance = red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE
    return Color(luminance, luminance, luminance, alpha)
}

private const val PRIMARY = "primary"
private const val MEDIA_PLAYER_BACKGROUND = "/static/images/card_media_player_bg.png"
private const val HALF = 0.5f
private const val LUMA_RED = 0.2126f
private const val LUMA_GREEN = 0.7152f
private const val LUMA_BLUE = 0.0722f

/** `card_media_player_bg.png`'s size in CSS pixels. */
private val BACKGROUND_WIDTH = 62.dp
private val BACKGROUND_HEIGHT = 50.dp
