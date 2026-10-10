package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.JammedLock
import io.homeassistant.companion.android.dashboard.moreinfo.LockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LockOpen
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.STATE_CONTROL_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.controls.STATE_CONTROL_THICKNESS
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlinx.coroutines.delay

/**
 * The controls of a lock's details, port of `more-info-lock` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-lock.ts): the tall switch, or a jammed lock's pulsing icon with unlock
 * and lock buttons, and the open button that asks for a second tap.
 */
@Composable
internal fun MoreInfoLock(info: LockMoreInfo, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        info.toggle?.let { StateToggleControl(it, onAction) }
        info.jammed?.let { JammedStatus(it) }
        info.open?.let { OpenButton(it, onAction) }
        info.jammed?.let { jammed ->
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = JAMMED_MAX_WIDTH),
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            ) {
                jammed.buttons.forEach { (label, action) ->
                    LockButton(label, Color.Unspecified, enabled = true, Modifier.weight(1f)) { onAction(action) }
                }
            }
        }
    }
}

/** A jammed lock's icon, pulsing in a tinted circle. */
@Composable
private fun JammedStatus(jammed: JammedLock) {
    val color = jammed.color?.toColor() ?: LocalHAColorScheme.current.colorFillDangerLoudResting
    val pulse by rememberInfiniteTransition(label = "jammed").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.width(STATE_CONTROL_THICKNESS).height(STATE_CONTROL_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(STATUS_SIZE)
                .alpha(pulse)
                .clip(CircleShape)
                .background(color.copy(alpha = TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(name = jammed.icon, tint = color, modifier = Modifier.size(STATUS_ICON_SIZE))
        }
    }
}

/** The open button: a first tap asks to confirm (for a few seconds), the second opens, then says it's done. */
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
            modifier = Modifier.height(BUTTON_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            DashboardIcon(
                name = "mdi:check",
                tint = colors.colorFillSuccessLoudResting,
                modifier = Modifier.size(HASize.X2L),
            )
            Text(open.doneLabel, style = HATextStyle.BodyMedium, color = colors.colorFillSuccessLoudResting)
        }
        return
    }
    val confirming = step == OpenStep.Confirm
    LockButton(
        label = if (confirming) open.confirmLabel else open.label,
        color = if (confirming) colors.colorFillWarningLoudResting else open.color?.toColor() ?: Color.Unspecified,
        enabled = open.enabled,
        modifier = Modifier.width(STATE_CONTROL_THICKNESS),
    ) {
        if (confirming) {
            onAction(open.action)
            step = OpenStep.Done
        } else {
            step = OpenStep.Confirm
        }
    }
}

/** Port of `ha-control-button` as the lock styles it: 60 high, rounded, on a tint of [color]. */
@Composable
private fun LockButton(label: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val tint = color.takeIf { it != Color.Unspecified } ?: colors.colorFillDisabledLoudResting
    Box(
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(HARadius.X3L))
            .background(tint.copy(alpha = TINT_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HATextStyle.Body, color = colors.colorTextPrimary)
    }
}

/** Where the open button is: waiting for a tap, for the confirming tap, or showing that it opened. */
private sealed interface OpenStep {
    data object Normal : OpenStep

    data object Confirm : OpenStep

    data object Done : OpenStep
}

private const val PULSE_MS = 500
private const val TINT_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f
private const val MILLIS = 1000L
private val BUTTON_HEIGHT = 60.dp
private val STATUS_SIZE = 144.dp
private val STATUS_ICON_SIZE = 80.dp
private val JAMMED_MAX_WIDTH = 400.dp
