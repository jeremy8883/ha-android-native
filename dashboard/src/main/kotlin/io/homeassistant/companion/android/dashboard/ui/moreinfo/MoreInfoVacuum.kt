package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumButton
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumMoreInfo
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.CONTROL_BUTTON_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus
import io.homeassistant.companion.android.dashboard.ui.controls.VacuumStatus
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The controls of a vacuum's details, port of `more-info-vacuum` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-vacuum.ts): the robot drawn as it is, the command buttons and the fan
 * speed menu. The battery shows beside the header's time.
 */
@Composable
internal fun MoreInfoVacuum(info: VacuumMoreInfo, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        VacuumStatus(info.visual, info.color?.toColor() ?: colors.colorFillDisabledLoudResting)
        if (info.buttons.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = GROUP_MAX_WIDTH).height(CONTROL_BUTTON_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            ) {
                info.buttons.forEach { VacuumCommand(it, onAction, Modifier.weight(1f)) }
            }
        }
        ControlSelectMenus(listOfNotNull(info.fanSpeed), onAction)
    }
}

/** Port of the `ha-control-button`s in their group: an icon on a quiet tint, greyed while it can't be used. */
@Composable
private fun VacuumCommand(button: VacuumButton, onAction: (CardAction) -> Unit, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (button.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(HARadius.X3L))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(enabled = button.enabled, role = Role.Button) { onAction(button.action) }
            .semantics { contentDescription = button.label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(button.icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
    }
}

private const val TINT_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f
private val GROUP_MAX_WIDTH = 400.dp
