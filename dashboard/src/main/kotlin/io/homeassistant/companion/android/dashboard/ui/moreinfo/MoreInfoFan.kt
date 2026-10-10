package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.FanMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.FanSpeedControl
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus
import io.homeassistant.companion.android.dashboard.ui.controls.StateControlSlider
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.controls.VerticalSelect
import kotlin.math.roundToInt

/**
 * The controls of a fan's details, port of `more-info-fan` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-fan.ts): the speed buttons, or the speed slider with a power button
 * under it, or a switch for a fan without speeds, then the preset, direction and oscillation menus.
 */
@Composable
internal fun MoreInfoFan(info: FanMoreInfo, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        when (val speed = info.speed) {
            is FanSpeedControl.Buttons -> VerticalSelect(speed.select, onAction)
            is FanSpeedControl.Slider ->
                StateControlSlider(speed.slider, valueText = { "${it.roundToInt()}%" }, onAction = onAction)
            null -> info.toggle?.let { StateToggleControl(it, onAction) }
        }
        info.power?.let { PowerButton(it, info.powerEnabled, onAction) }
        ControlSelectMenus(info.menus, onAction)
    }
}

/** Port of the `ha-outlined-icon-button` under the slider, turning the fan on or off. */
@Composable
private fun PowerButton(call: CardAction.CallService, enabled: Boolean, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = Modifier
            .size(POWER_SIZE)
            .clip(CircleShape)
            .border(HABorderWidth.S, colors.colorBorderNeutralQuiet, CircleShape)
            .clickable(enabled = enabled, role = Role.Button) { onAction(call) },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(
            name = "mdi:power",
            tint = if (enabled) colors.colorTextPrimary else colors.colorTextDisabled,
            modifier = Modifier.size(HASize.X2L),
        )
    }
}

private val POWER_SIZE = 48.dp
