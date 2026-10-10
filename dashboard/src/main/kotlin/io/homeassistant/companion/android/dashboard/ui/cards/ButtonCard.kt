package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.buttonCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * A button card, port of `hui-button-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-button-card.ts): a
 * large icon in the entity's colour, its name and state under it; a tap toggles what toggles, else opens its details.
 */
@Composable
internal fun ButtonCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val button by remember(card) { derivedStateOf { hass.value?.buttonCardModel(card) } }
    val model = button ?: return
    model.missing?.let { return CardWarning(it, modifier) }
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier.elementGestures(model.actions, interactions)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            model.icon?.let { icon ->
                DashboardIcon(
                    icon,
                    model.color?.toColor() ?: colors.colorTextSecondary,
                    Modifier.size(iconSize(model.iconHeight)),
                )
            }
            model.name?.let {
                Text(
                    it,
                    style = HATextStyle.Body,
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            model.state?.let {
                Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary, maxLines = 1)
            }
        }
    }
}

/** `icon_height` in pixels ("80px"), else the card's usual size. */
private fun iconSize(height: String?) = height?.removeSuffix("px")?.trim()?.toFloatOrNull()?.dp ?: ICON_SIZE

private val ICON_SIZE = 64.dp
