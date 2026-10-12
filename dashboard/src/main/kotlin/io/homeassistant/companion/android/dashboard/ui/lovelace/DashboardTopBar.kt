package io.homeassistant.companion.android.dashboard.ui.lovelace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.layout.DashboardMenu
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuHandler
import io.homeassistant.companion.android.dashboard.layout.NARROW_MAX_WIDTH_DP
import io.homeassistant.companion.android.dashboard.layout.ViewTabLabel
import io.homeassistant.companion.android.dashboard.layout.dashboardMenu
import io.homeassistant.companion.android.dashboard.ui.DashboardUiState
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.dialogs.notifications.hasNotifications
import io.homeassistant.companion.android.dashboard.ui.panels.PanelActions
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbarActions
import io.homeassistant.companion.android.dashboard.ui.panels.ToolbarIconButton as PanelIconButton
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlinx.coroutines.launch

/**
 * The toolbar: back to the page that opened it (else the menu, with a dot while there are notifications), then the
 * dashboard's view tabs when it has several top-level views, else the title, then the dashboard's actions
 * ([onMenuAction], hidden when `null`) or the buttons a native panel put in it ([panelActions]).
 *
 * Upstream: part of `hui-root`, `ha-top-app-bar-fixed` (src/panels/lovelace/hui-root.ts;
 * src/components/ha-top-app-bar-fixed.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = LocalHAColorScheme.current
    // HATopBar's colours, at the frontend's `--header-height` and `--ha-font-size-xl` title
    TopAppBar(
        expandedHeight = TOOLBAR_HEIGHT,
        colors = TopAppBarColors(
            containerColor = colors.colorSurfaceDefault,
            scrolledContainerColor = colors.colorSurfaceDefault,
            navigationIconContentColor = colors.colorOnNeutralQuiet,
            titleContentColor = colors.colorTextPrimary,
            actionIconContentColor = colors.colorOnNeutralQuiet,
        ),
        title = {
            val tabs = content?.takeIf { !it.isSubview && it.tabs.size > 1 }
            val shownLocalize = localize
            if (tabs != null && shownLocalize != null) {
                ViewTabs(tabs, shownLocalize, onSelectTab)
            } else {
                Text(
                    content?.title ?: panel?.title
                        ?: (uiState as? DashboardUiState.UnsupportedStrategy)?.title.orEmpty(),
                    fontSize = Css.FontSize.Xl,
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
 * The dashboard's top-level views as tabs, filling the toolbar's height, each as wide as its label and 16px either
 * side; the selected one underlined. When they don't fit they scroll, with an arrow at each end that has more tabs
 * past it, scrolling a width of tabs that way.
 *
 * Upstream: part of `hui-root`, `ha-tab-group` (src/panels/lovelace/hui-root.ts; src/components/ha-tab-group.ts)
 */
@Composable
private fun ViewTabs(content: DashboardUiState.Content, localize: Localize, onSelectTab: (String) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var navWidth by remember { mutableIntStateOf(0) }
    // `hasScrollControls`: the tabs are wider than their space (`hui-root` takes `.nav-container`'s room for the
    // arrows away, so they lie over the tabs)
    val scrolls = scroll.maxValue > 0
    val tabBounds = remember { mutableStateMapOf<Int, IntRange>() }
    // `setActiveTab` scrolls the selected tab into view
    LaunchedEffect(content.selectedTab, navWidth, tabBounds[content.selectedTab]) {
        val bounds = tabBounds[content.selectedTab] ?: return@LaunchedEffect
        val target = when {
            bounds.first < scroll.value -> bounds.first
            bounds.last > scroll.value + navWidth -> bounds.last - navWidth
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(target)
    }
    // The tab group starts right after the menu button, where Material's title slot leaves a gap
    Box(Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT).widenStart(TITLE_GAP)) {
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { navWidth = it.width }
                .horizontalScroll(scroll),
        ) {
            content.tabs.forEachIndexed { index, tab ->
                ViewTab(
                    selected = index == content.selectedTab,
                    onClick = { onSelectTab(tab.path) },
                    modifier = Modifier.onPlaced { coordinates ->
                        val start = coordinates.positionInParent().x.toInt()
                        tabBounds[index] = start..(start + coordinates.size.width)
                    },
                ) { ViewTabContent(tab.label, localize) }
            }
        }
        // `scrollOffset`: an arrow shows while more than 1px is hidden on its side
        if (scrolls && scroll.value > SCROLL_OFFSET_PX) {
            TabScrollButton(TabIcons.START, Modifier.align(Alignment.CenterStart), start = true) {
                scope.launch { scroll.animateScrollTo(scroll.value - navWidth) }
            }
        }
        if (scrolls && scroll.value < scroll.maxValue - SCROLL_OFFSET_PX) {
            TabScrollButton(TabIcons.END, Modifier.align(Alignment.CenterEnd), start = false) {
                scope.launch { scroll.animateScrollTo(scroll.value + navWidth) }
            }
        }
    }
}

/** Lays the element out [extra] wider on its start side, over its parent's start padding. */
private fun Modifier.widenStart(extra: Dp): Modifier = layout { measurable, constraints ->
    val shift = extra.roundToPx()
    val width = constraints.maxWidth + shift
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-shift, 0) }
}

/**
 * A view's tab: [content] in the tab's colour, 16px either side, the toolbar's height; the selected one in the
 * accent and underlined, the others at 80 % opacity.
 *
 * Upstream: `ha-tab-group-tab`, part of `hui-root` (src/components/ha-tab-group-tab.ts;
 * src/panels/lovelace/hui-root.ts)
 */
@Composable
private fun ViewTab(selected: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = LocalHAColorScheme.current
    val color = if (selected) colors.colorOnPrimaryNormal else colors.colorTextSecondary
    Box(
        modifier = modifier
            .height(TOOLBAR_HEIGHT)
            .alpha(if (selected) 1f else INACTIVE_TAB_ALPHA)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (selected) {
                    val track = TAB_TRACK_WIDTH.toPx()
                    drawRect(color, topLeft = Offset(0f, size.height - track), size = Size(size.width, track))
                }
            }
            .padding(horizontal = Css.Space.S4),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides Css.Text.Primary.copy(color = color, fontWeight = Css.FontWeight.Action),
        ) { content() }
    }
}

/**
 * An arrow scrolling the tabs, over a fade from the toolbar's colour.
 *
 * Upstream: part of `ha-tab-group` (`.scroll-button`), `hui-root` (src/components/ha-tab-group.ts;
 * src/panels/lovelace/hui-root.ts)
 */
@Composable
private fun TabScrollButton(icon: String, modifier: Modifier, start: Boolean, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val fade = listOf(colors.colorSurfaceDefault, colors.colorSurfaceDefault.copy(alpha = 0f))
    Box(
        modifier = modifier
            .width(SCROLL_BUTTON_WIDTH)
            .height(TOOLBAR_HEIGHT)
            .background(Brush.horizontalGradient(if (start) fade else fade.reversed()))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(SCROLL_ICON_SIZE))
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
            horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
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
    // Without the companion app, only the actions drawn natively
    val offered = (onAction as? DashboardMenuHandler)?.offered
    val menu by remember(hass, narrow, offered) {
        derivedStateOf {
            hass.value?.let { snapshot ->
                val user = snapshot.user
                dashboardMenu(
                    narrow = narrow,
                    isAdmin = user?.isAdmin == true,
                    recoveryMode = snapshot.config.recoveryMode,
                    components = snapshot.config.components,
                    offered = offered,
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
                    text = { Text(localize(action.labelKey), style = Css.Text.Body) },
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

/** The tab group's arrows. */
private object TabIcons {
    const val START = "mdi:chevron-left"
    const val END = "mdi:chevron-right"
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

/** `ha-tab-group`'s scroll buttons: `1.5em` of their 14px, with a 1em icon. */
private val SCROLL_BUTTON_WIDTH = 21.cssPx
private val SCROLL_ICON_SIZE = 14.cssPx

/** The space Material's top app bar leaves between the navigation icon and the title. */
private val TITLE_GAP = 4.dp

/** `ha-tab-group`'s `--track-width`. */
private val TAB_TRACK_WIDTH = 2.cssPx

/** `ha-tab-group-tab`'s `opacity: 0.8` while not active. */
private const val INACTIVE_TAB_ALPHA = 0.8f

/** `wa-tab-group`'s `scrollOffset`. */
private const val SCROLL_OFFSET_PX = 1

/** The frontend's `--header-height`, which the tabs fill as upstream's fill its header. */
private val TOOLBAR_HEIGHT = 56.dp

/** `ha-menu-button`'s dot: 12px, 9px from the top and 7px from the end of its 48px button, with a 2px border. */
private val DOT_SIZE = 12.dp
private val DOT_INSET_TOP = 9.dp
private val DOT_INSET_END = 7.dp
private val DOT_BORDER = 2.dp
