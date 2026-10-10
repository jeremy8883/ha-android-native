package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.StatusIcon
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * Port of `ha-control-button` as the lock and alarm details style it: 60 high, rounded, with its [label] on a tint
 * of [color] (neutral when unspecified).
 */
@Composable
internal fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val tint = color.takeIf { it != Color.Unspecified } ?: colors.colorFillDisabledLoudResting
    Box(
        modifier = modifier
            .height(CONTROL_BUTTON_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(HARadius.X3L))
            .background(tint.copy(alpha = TINT_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HATextStyle.Body, color = colors.colorTextPrimary)
    }
}

/** Port of `ha-control-button` with an icon: a rounded square on a quiet tint. */
@Composable
internal fun ControlButtonIcon(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
    }
}

/** The details' `.status`: the entity's icon in a tinted circle, pulsing while something is under way ([pulsing]). */
@Composable
internal fun PulsingStatusIcon(status: StatusIcon, modifier: Modifier = Modifier, pulsing: Boolean = true) {
    val color = status.color?.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
    val pulse by rememberInfiniteTransition(label = "status").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .size(STATUS_SIZE)
            .alpha(if (pulsing) pulse else 1f)
            .clip(CircleShape)
            .background(color.copy(alpha = TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(name = status.icon, tint = color, modifier = Modifier.size(STATUS_ICON_SIZE))
    }
}

/** The height of a [ControlButton]. */
internal val CONTROL_BUTTON_HEIGHT = 60.dp

private const val PULSE_MS = 500
private const val TINT_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f
private val STATUS_SIZE = 144.dp
private val STATUS_ICON_SIZE = 80.dp
