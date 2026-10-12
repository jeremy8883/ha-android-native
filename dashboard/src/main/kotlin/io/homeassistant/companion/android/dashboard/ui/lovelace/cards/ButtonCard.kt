package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

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
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.buttonCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint

/**
 * A button card, port of `hui-button-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-button-card.ts): a
 * large icon in the entity's colour, its name and state under it; a tap toggles what toggles, else opens its details.
 *
 * Upstream: `hui-button-card` (src/panels/lovelace/cards/hui-button-card.ts)
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
            modifier = Modifier.fillMaxWidth().padding(Css.Space.S4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
        ) {
            model.icon?.let { icon ->
                DashboardIcon(
                    icon,
                    entityIconTint(model.color, brightness = model.brightness),
                    Modifier.size(iconSize(model.iconHeight)),
                )
            }
            model.name?.let {
                Text(
                    it,
                    // `ha-card`'s `--ha-font-size-l` at `--ha-line-height-condensed`
                    style = Css.Text.Body.copy(fontSize = Css.FontSize.L, lineHeight = Css.LineHeight.Condensed),
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            model.state?.let {
                // `.state`'s `font-size: 0.9rem`
                Text(
                    it,
                    style = Css.Text.Body.copy(fontSize = STATE_FONT_SIZE, lineHeight = Css.LineHeight.Condensed),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `icon_height` in pixels ("80px"), else the card's usual size. */
private fun iconSize(height: String?) = height?.removeSuffix("px")?.trim()?.toFloatOrNull()?.dp ?: ICON_SIZE

private val ICON_SIZE = 64.dp

/** `.state`'s `font-size: 0.9rem` (the root's 14px). */
private val STATE_FONT_SIZE = 12.6.cssFontPx
