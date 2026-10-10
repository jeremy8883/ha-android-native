package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.ControlSelect
import io.homeassistant.companion.android.dashboard.moreinfo.MenuOption
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * Port of `ha-control-select` vertical, as the state controls size it: [select]'s options top to bottom, on a tint
 * of its background, the chosen one filled with its colour. An option shows its icon, else its label; choosing the
 * one already chosen does nothing.
 */
@Composable
internal fun VerticalSelect(
    select: ControlSelect,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = STATE_CONTROL_HEIGHT,
) {
    val colors = LocalHAColorScheme.current
    val tint = select.color?.toColor() ?: colors.colorFillPrimaryLoudResting
    val background = select.background?.toColor() ?: colors.colorFillDisabledLoudResting
    Column(
        modifier = modifier
            .size(STATE_CONTROL_THICKNESS, height)
            .alpha(if (select.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(STATE_CONTROL_RADIUS))
            .background(background.copy(alpha = BACKGROUND_ALPHA))
            .padding(SELECT_PADDING)
            .selectableGroup()
            .semantics { contentDescription = select.label },
        verticalArrangement = Arrangement.spacedBy(SELECT_PADDING),
    ) {
        select.options.forEach { option ->
            val selected = option.value == select.value
            SelectOption(option, selected, tint, select.enabled, Modifier.weight(1f)) {
                if (!selected) onAction(option.action)
            }
        }
    }
}

@Composable
private fun SelectOption(
    option: MenuOption,
    selected: Boolean,
    tint: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val fill by animateColorAsState(if (selected) tint else Color.Transparent, tween(TRANSITION_MS), label = "option")
    val content = if (selected) Color.White else colors.colorTextPrimary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(STATE_CONTROL_RADIUS - SELECT_PADDING))
            .background(fill)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = option.label },
        contentAlignment = Alignment.Center,
    ) {
        val icon = option.icon
        if (icon != null) {
            DashboardIcon(name = icon, tint = content, modifier = Modifier.size(HASize.X2L))
        } else {
            Text(option.label, style = HATextStyle.Body, color = content)
        }
    }
}

private const val TRANSITION_MS = 180
private const val BACKGROUND_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f
private val SELECT_PADDING = 6.dp
