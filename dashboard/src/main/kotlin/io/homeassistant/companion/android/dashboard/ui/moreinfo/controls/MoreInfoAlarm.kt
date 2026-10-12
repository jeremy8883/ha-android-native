package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.AlarmMoreInfo
import io.homeassistant.companion.android.dashboard.ui.components.ControlButton
import io.homeassistant.companion.android.dashboard.ui.components.PulsingStatusIcon
import io.homeassistant.companion.android.dashboard.ui.components.VerticalSelect
import io.homeassistant.companion.android.dashboard.ui.statecontrol.STATE_CONTROL_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * The controls of an alarm panel's details, port of `more-info-alarm_control_panel` (frontend@20260624.6
 * src/dialogs/more-info/controls/): its modes as tall buttons (at least 80 high each), or, while triggered, arming
 * or pending, its pulsing icon with a disarm button.
 *
 * Upstream: `more-info-alarm_control_panel` (src/dialogs/more-info/controls/more-info-alarm_control_panel.ts)
 */
@Composable
internal fun MoreInfoAlarm(info: AlarmMoreInfo, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Css.Space.S6),
    ) {
        info.modes?.let { modes ->
            VerticalSelect(modes, onAction, height = maxOf(STATE_CONTROL_HEIGHT, MODE_HEIGHT * modes.options.size))
        }
        info.status?.let { PulsingStatusIcon(it) }
        info.disarm?.let { (label, action) ->
            ControlButton(label, Modifier.fillMaxWidth().widthIn(max = DISARM_MAX_WIDTH)) { onAction(action) }
        }
    }
}

private val MODE_HEIGHT = 80.dp
private val DISARM_MAX_WIDTH = 400.dp
