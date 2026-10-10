package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * The details' menus in a row, scrolling sideways when they don't fit (`ha-more-info-control-select-container`,
 * frontend@20260624.6 src/dialogs/more-info/components/).
 */
@Composable
internal fun ControlSelectMenus(
    menus: List<SelectMenu>,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (menus.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3, Alignment.CenterHorizontally),
    ) {
        menus.forEach { ControlSelectMenu(it, onAction) }
    }
}

/**
 * Port of `ha-control-select-menu` (src/components/ha-control-select-menu.ts): a filled box with the selected
 * option's icon (or the menu's own when nothing is selected), the menu's label and the selected option, opening
 * the options with their icons. Choosing the selected option does nothing.
 */
@Composable
internal fun ControlSelectMenu(menu: SelectMenu, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val selected = menu.options.firstOrNull { it.value == menu.value }
    // A selected option without an icon shows none, as upstream renders its attribute icon
    val icon = if (menu.value != null && menu.optionIcons) selected?.icon else menu.icon
    val textColor = if (menu.enabled) colors.colorTextPrimary else colors.colorTextDisabled
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
                .height(HADimens.SPACE12)
                .clip(RoundedCornerShape(HARadius.L))
                .background(colors.colorFillDisabledLoudResting.copy(alpha = BACKGROUND_ALPHA))
                .clickable(enabled = menu.enabled, role = Role.DropdownList) { expanded = true }
                .padding(horizontal = MENU_PADDING, vertical = HADimens.SPACE1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MENU_PADDING),
        ) {
            Box(Modifier.size(HASize.XL)) { DashboardIcon(icon, textColor, Modifier.size(HASize.XL)) }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    menu.label,
                    style = HATextStyle.BodyMedium.copy(
                        fontSize = if (selected !=
                            null
                        ) {
                            HAFontSize.S
                        } else {
                            HAFontSize.M
                        },
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                selected?.let {
                    Text(
                        it.label,
                        style = HATextStyle.BodyMedium.copy(fontSize = HAFontSize.M),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        SelectMenuOptions(menu, expanded, onDismiss = { expanded = false }, onAction = onAction)
    }
}

/** The options of [menu] with their icons, the selected one highlighted. */
@Composable
private fun SelectMenuOptions(
    menu: SelectMenu,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (CardAction) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menu.options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label, style = HATextStyle.Body, color = colors.colorTextPrimary) },
                leadingIcon = option.icon?.let { name ->
                    { DashboardIcon(name, colors.colorTextSecondary, Modifier.size(HASize.X2L)) }
                },
                onClick = {
                    onDismiss()
                    if (option.value != menu.value) onAction(option.action)
                },
                modifier = if (option.value == menu.value) {
                    Modifier.background(colors.colorFillPrimaryQuietResting)
                } else {
                    Modifier
                },
            )
        }
    }
}

private const val BACKGROUND_ALPHA = 0.2f
private val MENU_MIN_WIDTH = 120.dp
private val MENU_MAX_WIDTH = 160.dp
private val MENU_PADDING = 10.dp
