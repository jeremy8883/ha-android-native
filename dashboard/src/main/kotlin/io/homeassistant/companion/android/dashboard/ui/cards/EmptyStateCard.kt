package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.emptyStateModel
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/** An empty state: a large icon, a title, a text and buttons, centred. */
@Composable
internal fun EmptyStateCard(card: CardConfig, interactions: CardInteractions, modifier: Modifier = Modifier) {
    val model = remember(card) { emptyStateModel(card) }
    val colors = LocalHAColorScheme.current
    val content = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            DashboardIcon(
                model.icon,
                model.iconColor?.toColor() ?: colors.colorTextSecondary,
                Modifier.size(HASize.X5L),
            )
            model.title?.let { Text(it, style = HATextStyle.HeadlineMedium, color = colors.colorTextPrimary) }
            model.content?.let { Text(it, style = HATextStyle.Body, color = colors.colorTextSecondary) }
            if (model.buttons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                    model.buttons.forEach { button ->
                        Row(
                            modifier = Modifier.elementGestures(button.actions, interactions).padding(HADimens.SPACE2),
                            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DashboardIcon(button.icon, colors.colorOnPrimaryNormal, Modifier.size(HASize.L))
                            button.text?.let {
                                Text(it, style = HATextStyle.BodyMedium, color = colors.colorOnPrimaryNormal)
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
