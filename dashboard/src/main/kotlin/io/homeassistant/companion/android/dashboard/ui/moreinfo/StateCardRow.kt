package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowSelect
import io.homeassistant.companion.android.dashboard.derive.RowTextInput
import io.homeassistant.companion.android.dashboard.derive.StateCard
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.RowControlContent
import io.homeassistant.companion.android.dashboard.ui.cards.ServerImage
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
import java.time.Instant

/**
 * Port of `state-card-content` in the dialog: the badge, the name over when it changed, then what the card shows on
 * the right (a switch, buttons or an input, else the state, with a second line under it). Dropdowns and text
 * fields carry the name as their label, in its place.
 */
@Composable
internal fun StateCardRow(
    card: StateCard,
    now: Instant,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHAColorScheme.current
    val right = card.right
    val labelled = right.control is RowSelect || (right.control as? RowTextInput)?.label?.isNotEmpty() == true
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        StateCardBadge(card)
        if (!labelled) {
            Column(Modifier.weight(1f)) {
                Text(
                    card.name,
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.changed?.let {
                    Text(
                        it,
                        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                        color = colors.colorTextSecondary,
                    )
                }
            }
        }
        val secondary = right.secondary
        if (right.control == null && secondary != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    right.state.orEmpty(),
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                    color = colors.colorTextPrimary,
                )
                Text(
                    secondary,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                    color = colors.colorTextSecondary,
                )
            }
        } else {
            RowControlContent(right.control, right.state, colors.colorTextPrimary, now, onAction)
        }
    }
}

@Composable
private fun StateCardBadge(card: StateCard) {
    val picture = card.badge.picture
    if (picture != null) {
        ServerImage(picture, card.name, Modifier.size(STATE_BADGE_SIZE).clip(CircleShape))
    } else {
        DashboardIcon(
            card.badge.icon,
            entityIconTint(card.badge.color, card.badge.unavailable, card.badge.brightness),
            Modifier.size(HASize.X2L),
        )
    }
}

/** `state-badge`'s 40 × 40. */
private val STATE_BADGE_SIZE = 40.dp
