package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.mediaControlModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/** A media control card: artwork, player name, what is playing and its controls. */
@Composable
internal fun MediaControlCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val media by remember(card) { derivedStateOf { hass.value?.mediaControlModel(card) } }
    val model = media ?: return UnsupportedCard(card.entity.orEmpty(), modifier)
    val colors = LocalHAColorScheme.current
    val textColor = if (model.off || model.unavailable) colors.colorTextSecondary else colors.colorTextPrimary
    DashboardCardSurface(modifier = modifier, active = !model.off && !model.unavailable) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (model.picture != null && !model.off && !model.unavailable) {
                ServerImage(
                    path = model.picture,
                    contentDescription = model.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ARTWORK_ASPECT_RATIO),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardIcon(model.icon, colors.colorTextSecondary, Modifier.size(HASize.XL))
                    Text(model.name, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary, maxLines = 1)
                }
                if (!model.unavailable) {
                    model.title?.let {
                        Text(
                            it,
                            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    model.subtitle?.let {
                        Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary, maxLines = 1)
                    }
                    if (model.controls.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
                            model.controls.forEach { control ->
                                IconButton(
                                    onClick = { interactions.onAction(control.action) },
                                    modifier = Modifier.semantics { contentDescription = control.label },
                                ) {
                                    DashboardIcon(control.icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val ARTWORK_ASPECT_RATIO = 16f / 9f
