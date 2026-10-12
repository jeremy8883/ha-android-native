package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.CommandButton
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumMoreInfo
import io.homeassistant.companion.android.dashboard.ui.components.CONTROL_BUTTON_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.components.ControlSelectMenus
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.moreinfo.components.CleanAreasButton
import io.homeassistant.companion.android.dashboard.ui.statecontrol.VacuumStatus
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The controls of a vacuum's details, port of `more-info-vacuum` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-vacuum.ts): the robot drawn as it is, the command buttons and the fan
 * speed menu, and cleaning by area ([onShowFull] opening the full dialog to map its areas). The battery shows beside
 * the header's time.
 *
 * Upstream: `more-info-vacuum` (src/dialogs/more-info/controls/more-info-vacuum.ts)
 */
@Composable
internal fun MoreInfoVacuum(
    info: VacuumMoreInfo,
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
    onShowFull: (() -> Unit)? = null,
) {
    val colors = LocalHAColorScheme.current
    MoreInfoControlsLayout {
        VacuumStatus(info.visual, info.color?.toColor() ?: colors.colorFillDisabledLoudResting)
        CommandButtonRow(info.buttons, onAction)
        ControlSelectMenus(listOfNotNull(info.fanSpeed), onAction)
        info.cleanAreasLabel?.let { CleanAreasButton(it, state, hass, onAction, onShowFull) }
    }
}

/**
 * Port of `ha-control-button-group`: the command buttons side by side.
 *
 * Upstream: `ha-control-button-group` (src/components/ha-control-button-group.ts)
 */
@Composable
internal fun CommandButtonRow(buttons: List<CommandButton>, onAction: (CardAction) -> Unit) {
    if (buttons.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = GROUP_MAX_WIDTH).height(CONTROL_BUTTON_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
    ) {
        buttons.forEach { VacuumCommand(it, onAction, Modifier.weight(1f)) }
    }
}

/**
 * Port of the `ha-control-button`s in their group: an icon on a quiet tint, greyed while it can't be used.
 *
 * Upstream: `ha-control-button` (src/components/ha-control-button.ts)
 */
@Composable
private fun VacuumCommand(button: CommandButton, onAction: (CardAction) -> Unit, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (button.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(Css.Radius.X3l))
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
