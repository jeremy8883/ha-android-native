package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.StateToggle
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * Port of `ha-state-control-toggle` (frontend@20260624.6 src/state-control/ha-state-control-toggle.ts): a tall
 * switch with the on side at the top, or separate on and off buttons when the state is only assumed.
 */
@Composable
internal fun StateToggleControl(toggle: StateToggle, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    val onColor = toggle.onColor?.toColor() ?: colors.colorFillPrimaryLoudResting
    val offColor = toggle.offColor?.toColor() ?: colors.colorFillDisabledLoudResting
    if (toggle.buttons) {
        ToggleButtons(toggle, onColor, offColor, onAction, modifier)
    } else {
        ToggleSwitch(toggle, onColor, offColor, onAction, modifier)
    }
}

/** Port of `ha-control-switch`, vertical and reversed: the knob slides up when on. */
@Composable
private fun ToggleSwitch(
    toggle: StateToggle,
    onColor: Color,
    offColor: Color,
    onAction: (CardAction) -> Unit,
    modifier: Modifier,
) {
    val color by animateColorAsState(if (toggle.checked) onColor else offColor, tween(TRANSITION_MS), label = "switch")
    val shape = RoundedCornerShape(STATE_CONTROL_RADIUS)
    val travel = STATE_CONTROL_HEIGHT / 2 - SWITCH_PADDING
    val knobOffset by animateDpAsState(if (toggle.checked) 0.dp else travel, tween(TRANSITION_MS), label = "knob")
    Box(
        modifier = modifier
            .size(STATE_CONTROL_THICKNESS, STATE_CONTROL_HEIGHT)
            .alpha(if (toggle.enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .toggleable(value = toggle.checked, enabled = toggle.enabled, role = Role.Switch) { on ->
                onAction(if (on) toggle.turnOn else toggle.turnOff)
            }
            .semantics { contentDescription = toggle.label },
    ) {
        Box(Modifier.matchParentSize().background(color.copy(alpha = BACKGROUND_ALPHA)))
        Box(
            modifier = Modifier
                .padding(SWITCH_PADDING)
                .offset(y = knobOffset)
                .fillMaxWidth()
                .fillMaxHeight(KNOB_FRACTION)
                .clip(RoundedCornerShape(STATE_CONTROL_RADIUS - SWITCH_PADDING))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(
                name = if (toggle.checked) toggle.onIcon else toggle.offIcon,
                tint = Color.White,
                modifier = Modifier.size(HASize.X2L),
            )
        }
    }
}

/** Two tall buttons, on above off, the one matching the state filled with its colour. */
@Composable
private fun ToggleButtons(
    toggle: StateToggle,
    onColor: Color,
    offColor: Color,
    onAction: (CardAction) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.size(STATE_CONTROL_THICKNESS, STATE_CONTROL_HEIGHT).padding(SWITCH_PADDING),
        verticalArrangement = Arrangement.spacedBy(SWITCH_PADDING),
    ) {
        ToggleButton(toggle.turnOnLabel, toggle.onIcon, toggle.checked, onColor, toggle.enabled, Modifier.weight(1f)) {
            onAction(toggle.turnOn)
        }
        ToggleButton(
            toggle.turnOffLabel,
            toggle.offIcon,
            !toggle.checked,
            offColor,
            toggle.enabled,
            Modifier.weight(1f),
        ) { onAction(toggle.turnOff) }
    }
}

@Composable
private fun ToggleButton(
    label: String,
    icon: String,
    active: Boolean,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(STATE_CONTROL_RADIUS))
            .background(if (active) color else colors.colorFillDisabledQuietResting)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(
            name = icon,
            tint = if (active) Color.White else colors.colorTextPrimary,
            modifier = Modifier.size(HASize.X2L),
        )
    }
}

private const val TRANSITION_MS = 180
private const val BACKGROUND_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f
private const val KNOB_FRACTION = 0.5f
private val SWITCH_PADDING = 6.dp
