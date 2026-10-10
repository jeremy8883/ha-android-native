package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.GroupablePlayer
import io.homeassistant.companion.android.dashboard.moreinfo.MediaGrouping
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/** The grouping button: speakers, with how many play together when more than one. Opens [MediaGroupingDialog]. */
@Composable
internal fun MediaGroupingButton(grouping: MediaGrouping, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Box {
            DashboardIcon(
                "mdi:speaker-multiple",
                colors.colorTextPrimary,
                Modifier.size(HASize.X2L).semantics { contentDescription = grouping.label },
            )
            grouping.count?.let { count ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(colors.colorFillPrimaryLoudResting),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$count",
                        style = HATextStyle.BodyMedium.copy(
                            fontSize =
                            HATextStyle.BodyMedium.fontSize * BADGE_TEXT,
                        ),
                        color = colors.colorOnPrimaryLoud,
                    )
                }
            }
        }
    }
    if (open) MediaGroupingDialog(grouping, onAction) { open = false }
}

/**
 * Port of `dialog-join-media-players`: the player itself (always on) and the others it can play with, each with a
 * switch, "Select all", and apply (joining the chosen, unjoining those no longer chosen) or cancel.
 */
@Composable
private fun MediaGroupingDialog(grouping: MediaGrouping, onAction: (CardAction) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(grouping.members) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(grouping.title, style = HATextStyle.HeadlineMedium, modifier = Modifier.weight(1f))
                HAPlainButton(grouping.selectAllLabel, { selected = grouping.candidates.map { it.entityId }.toSet() })
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlayerToggle(grouping.player, checked = true, enabled = false) {}
                grouping.candidates.forEach { player ->
                    PlayerToggle(player, player.entityId in selected, enabled = true) { on ->
                        selected = if (on) selected + player.entityId else selected - player.entityId
                    }
                }
            }
        },
        confirmButton = {
            HAPlainButton(grouping.applyLabel, {
                grouping.apply(selected).forEach(onAction)
                onDismiss()
            })
        },
        dismissButton = { HAPlainButton(grouping.cancelLabel, onDismiss) },
    )
}

/** Port of `ha-media-player-toggle`: the player's icon, name and where it is, and its switch. */
@Composable
private fun PlayerToggle(player: GroupablePlayer, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = HADimens.SPACE1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        DashboardIcon(player.icon, colors.colorTextSecondary, Modifier.size(HASize.X2L))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = HATextStyle.Body, color = colors.colorTextPrimary)
            player.context?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary) }
        }
        HASwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private const val BADGE_TEXT = 0.75f
private val BADGE_SIZE = 16.dp
private val BADGE_OFFSET = 6.dp
private val ROW_HEIGHT = 56.dp
