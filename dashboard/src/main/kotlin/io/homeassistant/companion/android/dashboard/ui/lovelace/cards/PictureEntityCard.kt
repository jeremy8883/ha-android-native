package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.ImageFitMode
import io.homeassistant.companion.android.dashboard.derive.PictureEntityModel
import io.homeassistant.companion.android.dashboard.derive.pictureEntityModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.LocalBrandsToken
import io.homeassistant.companion.android.dashboard.ui.components.LocalServerUrl
import io.homeassistant.companion.android.dashboard.ui.components.resolveServerUrl
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx

/**
 * A picture of an entity (a camera snapshot, an image entity, a person) with its name and state over it. The picture
 * keeps `aspect_ratio`, or its own shape (16:9 until it loads), or fills the card when a section fixes its rows.
 *
 * Upstream: `hui-picture-entity-card` (src/panels/lovelace/cards/hui-picture-entity-card.ts)
 */
@Composable
internal fun PictureEntityCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val picture by remember(card) { derivedStateOf { hass.value?.pictureEntityModel(card) } }
    val model = picture ?: return UnsupportedCard(card.entity.orEmpty(), modifier)
    val colors = LocalHAColorScheme.current
    // The image's own width over height once loaded, like `hui-image`'s `_lastImageHeight`
    var natural by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier) {
        // A section's fixed rows give the card a minimum height: upstream then ignores `aspect_ratio` and fills it
        val fixedHeight = if (constraints.minHeight > 0) minHeight else null
        val ratio = model.aspectRatio?.toFloat()
        val sizing = when {
            fixedHeight != null -> Modifier.height(fixedHeight)
            else -> Modifier.aspectRatio(ratio ?: natural ?: DEFAULT_ASPECT_RATIO)
        }
        DashboardCardSurface(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sizing)
                    // `hui-image` has no background: only the placeholder gets one
                    .then(
                        if (model.image ==
                            null
                        ) {
                            Modifier.background(colors.colorFillNeutralQuietResting)
                        } else {
                            Modifier
                        },
                    )
                    .elementGestures(model.actions, interactions),
            ) {
                PictureEntityImage(model, onNaturalRatio = { natural = it })
                PictureEntityFooter(model.name, model.state, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/**
 * The picture, or an icon while there is none.
 *
 * Upstream: part of `hui-image` (src/panels/lovelace/components/hui-image.ts)
 */
@Composable
private fun BoxScope.PictureEntityImage(model: PictureEntityModel, onNaturalRatio: (Float) -> Unit) {
    val url = resolveServerUrl(model.image, LocalServerUrl.current, LocalBrandsToken.current)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = model.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = when (model.fitMode) {
                ImageFitMode.COVER -> ContentScale.Crop
                ImageFitMode.CONTAIN -> ContentScale.Fit
                ImageFitMode.FILL -> ContentScale.FillBounds
            },
            onSuccess = { success ->
                val size = success.painter.intrinsicSize
                if (size.isSpecified && size.width > 0 && size.height > 0) onNaturalRatio(size.width / size.height)
            },
        )
    } else {
        DashboardIcon(
            name = if (model.unavailable) UNAVAILABLE_ICON else LOADING_ICON,
            tint = LocalHAColorScheme.current.colorTextDisabled,
            modifier = Modifier.size(HASize.X5L).align(Alignment.Center),
        )
    }
}

/**
 * The dark footer over the picture: name and state at either end, or a lone one centred.
 *
 * Upstream: part of `hui-picture-entity-card` (src/panels/lovelace/cards/hui-picture-entity-card.ts)
 */
@Composable
private fun PictureEntityFooter(name: String?, state: String?, modifier: Modifier) {
    if (name == null && state == null) return
    // White text on a dark footer like upstream's, so it stays readable in both themes
    val style = TextStyle(fontSize = Css.FontSize.L, lineHeight = FOOTER_LINE_HEIGHT, color = Color.White)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = FOOTER_ALPHA))
            .padding(Css.Space.S4),
        horizontalArrangement = if (name != null && state != null) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        name?.let { Text(it, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        state?.let { Text(it, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private const val DEFAULT_ASPECT_RATIO = 16f / 9f
private const val FOOTER_ALPHA = 0.3f
private val FOOTER_LINE_HEIGHT = 16.cssFontPx
private const val UNAVAILABLE_ICON = "mdi:video-off"
private const val LOADING_ICON = "mdi:image"
