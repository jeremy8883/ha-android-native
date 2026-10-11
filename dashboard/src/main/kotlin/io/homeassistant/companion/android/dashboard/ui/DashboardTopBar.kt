package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.layout.DashboardMenu
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.layout.NARROW_MAX_WIDTH_DP
import io.homeassistant.companion.android.dashboard.layout.ViewTabLabel
import io.homeassistant.companion.android.dashboard.layout.dashboardMenu
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.panels.PanelActions
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbarActions
import io.homeassistant.companion.android.dashboard.ui.panels.ToolbarIconButton as PanelIconButton
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The toolbar: back to the page that opened it (else the menu, with a dot while there are notifications), then the
 * dashboard's view tabs when it has several top-level views, else the title, then the dashboard's actions
 * ([onMenuAction], hidden when `null`) or the buttons a native panel put in it ([panelActions]).
 *
 * Upstream: part of `hui-root`, `ha-top-app-bar-fixed` (src/panels/lovelace/hui-root.ts;
 * src/components/ha-top-app-bar-fixed.ts)
 */
@Composable
internal fun DashboardTopBar(
    uiState: DashboardUiState,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    panelActions: PanelActions,
    hass: State<HassSnapshot?> = remember { mutableStateOf(null) },
    onSelectTab: (String) -> Unit = {},
    onMenuAction: ((DashboardMenuAction) -> Unit)? = null,
    hasNotifications: Boolean = false,
) {
    val content = uiState as? DashboardUiState.Content
    val panel = uiState as? DashboardUiState.Panel
    val goesBack = content?.showsBack == true || panel?.canGoBack == true
    val localize by remember(hass) { derivedStateOf { hass.value?.localize } }
    HATopBar(
        title = {
            val tabs = content?.takeIf { !it.isSubview && it.tabs.size > 1 }
            val shownLocalize = localize
            if (tabs != null && shownLocalize != null) {
                ViewTabs(tabs, shownLocalize, onSelectTab)
            } else {
                Text(
                    content?.title ?: panel?.title
                        ?: (uiState as? DashboardUiState.UnsupportedStrategy)?.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (goesBack) {
                ToolbarIconButton(ToolbarIcons.BACK, stringResource(commonR.string.navigate_up), onBack)
            } else {
                MenuButton(hasNotifications, onOpenMenu)
            }
        },
        actions = {
            PanelToolbarActions(panelActions)
            if (content != null && onMenuAction != null) DashboardMenuActions(hass, onMenuAction)
        },
    )
}

/**
 * The dashboard's top-level views as tabs, filling the toolbar's height.
 *
 * Upstream: part of `hui-root`, `ha-tab-group` (src/panels/lovelace/hui-root.ts; src/components/ha-tab-group.ts)
 */
@Composable
private fun ViewTabs(content: DashboardUiState.Content, localize: Localize, onSelectTab: (String) -> Unit) {
    val colors = LocalHAColorScheme.current
    PrimaryScrollableTabRow(
        selectedTabIndex = content.selectedTab,
        modifier = Modifier.height(TOOLBAR_HEIGHT),
        containerColor = colors.colorSurfaceDefault,
        contentColor = colors.colorOnPrimaryNormal,
        edgePadding = HADimens.SPACE0,
        divider = {},
    ) {
        content.tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == content.selectedTab,
                onClick = { onSelectTab(tab.path) },
                text = { ViewTabContent(tab.label, localize) },
                modifier = Modifier.height(TOOLBAR_HEIGHT),
                selectedContentColor = colors.colorOnPrimaryNormal,
                unselectedContentColor = colors.colorTextSecondary,
            )
        }
    }
}

/**
 * A view tab's icon, title or both, side by side; "Unnamed view" for a view with neither.
 *
 * Upstream: part of `hui-root` (src/panels/lovelace/hui-root.ts)
 */
@Composable
private fun ViewTabContent(label: ViewTabLabel, localize: Localize) {
    // The tab's content colour, selected or not
    val color = LocalContentColor.current
    when (label) {
        is ViewTabLabel.IconOnly -> DashboardIcon(
            name = label.icon,
            tint = color,
            modifier = Modifier.size(HASize.X2L).semantics { label.title?.let { contentDescription = it } },
        )
        is ViewTabLabel.IconAndTitle -> Row(
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardIcon(name = label.icon, tint = color, modifier = Modifier.size(HASize.X2L))
            Text(label.title, maxLines = 1)
        }
        is ViewTabLabel.TitleOnly -> Text(label.title ?: localize(UNNAMED_VIEW), maxLines = 1)
    }
}

/**
 * The dashboard's actions: those shown as buttons, then the others behind the menu button.
 *
 * Upstream: part of `hui-root` (src/panels/lovelace/hui-root.ts)
 */
@Composable
private fun DashboardMenuActions(hass: State<HassSnapshot?>, onAction: (DashboardMenuAction) -> Unit) {
    val narrow = LocalConfiguration.current.screenWidthDp <= NARROW_MAX_WIDTH_DP
    val menu by remember(hass, narrow) {
        derivedStateOf {
            hass.value?.let { snapshot ->
                val user = snapshot.user
                dashboardMenu(
                    narrow = narrow,
                    isAdmin = user?.isAdmin == true,
                    recoveryMode = snapshot.config.recoveryMode,
                    components = snapshot.config.components,
                ) to snapshot.localize
            }
        }
    }
    val (shown, localize) = menu ?: return
    shown.buttons.forEach { action -> PanelIconButton(action.icon, localize(action.labelKey)) { onAction(action) } }
    OverflowMenu(shown, localize, onAction)
}

/**
 * The menu button and the actions behind it.
 *
 * Upstream: part of `hui-root`, `ha-dropdown` (src/panels/lovelace/hui-root.ts; src/components/ha-dropdown.ts)
 */
@Composable
private fun OverflowMenu(menu: DashboardMenu, localize: Localize, onAction: (DashboardMenuAction) -> Unit) {
    if (menu.overflow.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        PanelIconButton(ToolbarIcons.OVERFLOW, localize(OPEN_MENU)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.overflow.forEach { action ->
                DropdownMenuItem(
                    text = { Text(localize(action.labelKey), style = HATextStyle.Body) },
                    leadingIcon = { DashboardIcon(action.icon, LocalHAColorScheme.current.colorTextSecondary) },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

/**
 * The menu button, with a dot in the accent colour while there are notifications.
 *
 * Upstream: `ha-menu-button` (src/components/ha-menu-button.ts)
 */
@Composable
private fun MenuButton(hasNotifications: Boolean, onClick: () -> Unit) {
    Box {
        ToolbarIconButton(ToolbarIcons.MENU, stringResource(R.string.native_dashboard_menu), onClick)
        if (hasNotifications) {
            val colors = LocalHAColorScheme.current
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -DOT_INSET_END, y = DOT_INSET_TOP)
                    .size(DOT_SIZE)
                    .border(DOT_BORDER, colors.colorSurfaceDefault, CircleShape)
                    .background(
                        DisplayColor.State(listOf(ACCENT)).toColor() ?: colors.colorFillPrimaryLoudResting,
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * The toolbar's back button, labelled for accessibility.
 *
 * Upstream: `ha-menu-button`, `ha-icon-button-arrow-prev` (src/components/ha-menu-button.ts;
 * src/components/ha-icon-button-arrow-prev.ts)
 */
@Composable
private fun ToolbarIconButton(icon: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        DashboardIcon(name = icon, tint = LocalHAColorScheme.current.colorTextPrimary)
    }
}

/** The toolbar's icons. */
private object ToolbarIcons {
    const val BACK = "mdi:arrow-left"
    const val MENU = "mdi:menu"
    const val OVERFLOW = "mdi:dots-vertical"
}

private const val UNNAMED_VIEW = "ui.panel.lovelace.views.unnamed_view"
private const val OPEN_MENU = "ui.panel.lovelace.editor.menu.open"
private const val ACCENT = "accent-color"

/** Material's toolbar height, which the tabs fill as upstream's fill its header. */
private val TOOLBAR_HEIGHT = 64.dp

/** `ha-menu-button`'s dot: 12px, 9px from the top and 7px from the end of its 48px button, with a 2px border. */
private val DOT_SIZE = 12.dp
private val DOT_INSET_TOP = 9.dp
private val DOT_INSET_END = 7.dp
private val DOT_BORDER = 2.dp
