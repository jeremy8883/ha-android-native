package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.derive.ButtonAppearance
import io.homeassistant.companion.android.dashboard.derive.ButtonColorVariant
import io.homeassistant.companion.android.dashboard.derive.EmptyStateButton
import io.homeassistant.companion.android.dashboard.derive.emptyStateModel
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.theme.FrontendTextStyle
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/** `.container`'s `max-width`. */
private val CONTAINER_MAX_WIDTH = 640.dp

/**
 * An empty state: a large icon, a title, a text and buttons, centred.
 *
 * Upstream: `hui-empty-state-card` (src/panels/lovelace/cards/hui-empty-state-card.ts)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyStateCard(card: CardConfig, interactions: CardInteractions, modifier: Modifier = Modifier) {
    val model = remember(card) { emptyStateModel(card) }
    val colors = LocalHAColorScheme.current
    val content = @Composable {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTAINER_MAX_WIDTH)
                    .padding(vertical = HADimens.SPACE8, horizontal = HADimens.SPACE4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                DashboardIcon(
                    model.icon,
                    model.iconColor?.toColor() ?: colors.colorTextSecondary,
                    Modifier.size(HADimens.SPACE16),
                )
                model.title?.let {
                    Text(
                        it,
                        style = TextStyle(fontSize = HAFontSize.XL, fontWeight = FontWeight.Medium),
                        color = colors.colorTextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                model.content?.let {
                    Text(
                        it,
                        style = FrontendTextStyle.Body,
                        color = colors.colorTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (model.buttons.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    ) {
                        model.buttons.forEach { button ->
                            EmptyStateCardButton(button) {
                                if (button.actions.tap) interactions.onGesture(button.actions.config, Gesture.TAP)
                            }
                        }
                    }
                }
            }
        }
    }
    if (model.contentOnly) {
        Column(modifier = modifier) {
            content()
        }
    } else {
        DashboardCardSurface(modifier = modifier) { content() }
    }
}

/**
 * One of the card's buttons, an `ha-button` with its appearance and variant.
 *
 * Upstream: part of `hui-empty-state-card` (src/panels/lovelace/cards/hui-empty-state-card.ts)
 */
@Composable
private fun EmptyStateCardButton(button: EmptyStateButton, onClick: () -> Unit) {
    val text = button.text.orEmpty()
    val variant = button.variant.toButtonVariant()
    val prefix: (@Composable BoxScope.() -> Unit)? = button.icon?.let { icon ->
        { DashboardIcon(icon, LocalContentColor.current, Modifier.size(HASize.X2L)) }
    }
    when (button.appearance) {
        ButtonAppearance.ACCENT -> HAAccentButton(text, onClick, variant = variant, prefix = prefix)
        ButtonAppearance.FILLED -> HAFilledButton(text, onClick, variant = variant, prefix = prefix)
        ButtonAppearance.PLAIN -> HAPlainButton(text, onClick, variant = variant, prefix = prefix)
    }
}

private fun ButtonColorVariant.toButtonVariant(): ButtonVariant = when (this) {
    ButtonColorVariant.BRAND -> ButtonVariant.PRIMARY
    ButtonColorVariant.NEUTRAL -> ButtonVariant.NEUTRAL
    ButtonColorVariant.SUCCESS -> ButtonVariant.SUCCESS
    ButtonColorVariant.WARNING -> ButtonVariant.WARNING
    ButtonColorVariant.DANGER -> ButtonVariant.DANGER
}
