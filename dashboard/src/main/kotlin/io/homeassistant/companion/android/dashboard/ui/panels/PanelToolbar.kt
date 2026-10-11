package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/** A toolbar button of a native panel: its [icon], [label] and what it does, greyed out unless [enabled]. */
internal data class PanelAction(
    val icon: String,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** What a native panel shows in the toolbar: its [buttons], then a menu of [menu] items when it has any. */
internal data class PanelActions(
    val buttons: List<PanelAction> = emptyList(),
    val menu: List<PanelAction> = emptyList(),
    val menuLabel: String = "",
)

/** The toolbar buttons of the native panel shown, which the panel sets itself (refresh, its menu...). */
@Stable
internal class PanelToolbar {
    /** The buttons and menu. */
    var actions: PanelActions by mutableStateOf(PanelActions())
        private set

    /** Shows [actions] in the toolbar while the calling panel is shown. */
    @Composable
    fun Actions(actions: PanelActions) {
        SideEffect { this.actions = actions }
        DisposableEffect(this) { onDispose { this@PanelToolbar.actions = PanelActions() } }
    }
}

/** The toolbar's panel buttons, then the menu button opening the panel's menu. */
@Composable
internal fun PanelToolbarActions(actions: PanelActions) {
    actions.buttons.forEach { ToolbarIconButton(it.icon, it.label, it.enabled, it.onClick) }
    if (actions.menu.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIconButton(MENU_ICON, actions.menuLabel) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.menu.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = HATextStyle.Body) },
                    leadingIcon = { DashboardIcon(item.icon, LocalHAColorScheme.current.colorTextSecondary) },
                    enabled = item.enabled,
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** A toolbar icon button, labelled for accessibility. */
@Composable
internal fun ToolbarIconButton(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        DashboardIcon(
            name = icon,
            tint = if (enabled) colors.colorTextPrimary else colors.colorTextDisabled,
            modifier = Modifier.size(HASize.X2L),
        )
    }
}

private const val MENU_ICON = "mdi:dots-vertical"
