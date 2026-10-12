package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.JammedLock
import io.homeassistant.companion.android.dashboard.moreinfo.LockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LockOpen
import io.homeassistant.companion.android.dashboard.ui.components.CONTROL_BUTTON_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.components.ControlButton
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.PulsingStatusIcon
import io.homeassistant.companion.android.dashboard.ui.statecontrol.STATE_CONTROL_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.statecontrol.STATE_CONTROL_THICKNESS
import io.homeassistant.companion.android.dashboard.ui.statecontrol.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlinx.coroutines.delay

/**
 * The controls of a lock's details, port of `more-info-lock` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-lock.ts): the tall switch, or a jammed lock's pulsing icon with unlock
 * and lock buttons, and the open button that asks for a second tap.
 *
 * Upstream: `more-info-lock` (src/dialogs/more-info/controls/more-info-lock.ts)
 */
@Composable
internal fun MoreInfoLock(info: LockMoreInfo, onAction: (CardAction) -> Unit) {
    // The jammed buttons' `<div>` follows the controls whatever it holds
    MoreInfoControlsLayout(bottom = { info.jammed?.let { JammedButtons(it, onAction) } }) {
        info.toggle?.let { StateToggleControl(it, onAction) }
        info.jammed?.let { jammed ->
            Box(Modifier.height(STATE_CONTROL_HEIGHT), contentAlignment = Alignment.Center) {
                PulsingStatusIcon(jammed.status)
            }
        }
        info.open?.let { OpenButton(it, onAction) }
    }
}

/**
 * The unlock and lock buttons of a jammed lock.
 *
 * Upstream: part of `more-info-lock` (src/dialogs/more-info/controls/more-info-lock.ts)
 */
@Composable
private fun JammedButtons(jammed: JammedLock, onAction: (CardAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = JAMMED_MAX_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
    ) {
        jammed.buttons.forEach { (label, action) ->
            ControlButton(label, Modifier.weight(1f)) { onAction(action) }
        }
    }
}

/**
 * The open button: a first tap asks to confirm (for a few seconds), the second opens, then says it's done.
 *
 * Upstream: part of `more-info-lock` (src/dialogs/more-info/controls/more-info-lock.ts)
 */
@Composable
private fun OpenButton(open: LockOpen, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    var step by remember { mutableStateOf<OpenStep>(OpenStep.Normal) }
    LaunchedEffect(step) {
        when (step) {
            OpenStep.Confirm -> delay(LockOpen.CONFIRM_SECONDS * MILLIS)
            OpenStep.Done -> delay(LockOpen.DONE_SECONDS * MILLIS)
            OpenStep.Normal -> return@LaunchedEffect
        }
        step = OpenStep.Normal
    }
    if (step == OpenStep.Done) {
        Row(
            modifier = Modifier.height(CONTROL_BUTTON_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        ) {
            DashboardIcon(
                name = "mdi:check",
                tint = colors.colorFillSuccessLoudResting,
                modifier = Modifier.size(HASize.X2L),
            )
            Text(
                open.doneLabel,
                style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                color = colors.colorFillSuccessLoudResting,
            )
        }
        return
    }
    val confirming = step == OpenStep.Confirm
    ControlButton(
        label = if (confirming) open.confirmLabel else open.label,
        modifier = Modifier.width(STATE_CONTROL_THICKNESS),
        color = if (confirming) colors.colorFillWarningLoudResting else open.color?.toColor() ?: Color.Unspecified,
        enabled = open.enabled,
    ) {
        if (confirming) {
            onAction(open.action)
            step = OpenStep.Done
        } else {
            step = OpenStep.Confirm
        }
    }
}

/** Where the open button is: waiting for a tap, for the confirming tap, or showing that it opened. */
private sealed interface OpenStep {
    data object Normal : OpenStep

    data object Confirm : OpenStep

    data object Done : OpenStep
}

private const val MILLIS = 1000L
private val JAMMED_MAX_WIDTH = 400.dp
