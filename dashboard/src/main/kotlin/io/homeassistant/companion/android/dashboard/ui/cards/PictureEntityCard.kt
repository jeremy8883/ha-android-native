package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.pictureEntityModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/** A picture of an entity (a camera snapshot, an image entity, a person) with its name and state over it. */
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
    DashboardCardSurface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DEFAULT_ASPECT_RATIO)
                .background(colors.colorFillNeutralQuietResting)
                .elementGestures(model.actions, interactions),
        ) {
            if (model.image != null) {
                ServerImage(model.image, contentDescription = model.name, modifier = Modifier.fillMaxSize())
            } else {
                DashboardIcon(
                    name = if (model.unavailable) UNAVAILABLE_ICON else LOADING_ICON,
                    tint = colors.colorTextDisabled,
                    modifier = Modifier.size(HASize.X5L).align(Alignment.Center),
                )
            }
            if (model.name != null || model.state != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = FOOTER_ALPHA))
                        .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // A dark footer over the picture, like upstream's, so the text stays white in both themes
                    model.name?.let { Text(it, style = HATextStyle.BodyMedium, color = Color.White, maxLines = 1) }
                    model.state?.let { Text(it, style = HATextStyle.BodyMedium, color = Color.White, maxLines = 1) }
                }
            }
        }
    }
}

private const val DEFAULT_ASPECT_RATIO = 16f / 9f
private const val FOOTER_ALPHA = 0.3f
private const val UNAVAILABLE_ICON = "mdi:video-off"
private const val LOADING_ICON = "mdi:image"
