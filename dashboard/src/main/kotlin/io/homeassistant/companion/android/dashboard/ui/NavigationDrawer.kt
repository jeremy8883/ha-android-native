package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.navigation.SidebarItem
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * The app's navigation drawer, in place of the frontend's sidebar: the panels shown natively in the user's order,
 * then under their own heading those that open in the companion app, then Settings (for admins) and the profile,
 * like `ha-sidebar`'s fixed entries.
 *
 * @param selected the url path of the panel shown now
 * @param drawerState the drawer's state, which back closes (with Material's predictive back animation)
 * @param sidebar the panels, or why they couldn't be loaded, with [onRetry] to try again
 * @param onSwitchServer shows the server picker; `null` hides the entry (a single server)
 * @param onlyDashboards lists only the dashboards, when other pages can't be opened
 * @param onOpenSettings opens the app's own settings, in place of the frontend's Settings and profile; `null` keeps
 *   those
 * @param onOpen called with the path of the chosen entry
 */
@Composable
internal fun NavigationDrawerContent(
    drawerState: DrawerState,
    sidebar: Loadable<SidebarState>,
    selected: String?,
    onSwitchServer: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onlyDashboards: Boolean = false,
    onRetry: () -> Unit = {},
    onOpen: (String) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.colorFillPrimaryQuietResting,
        selectedTextColor = colors.colorOnPrimaryNormal,
        unselectedTextColor = colors.colorTextPrimary,
    )

    @Composable
    fun Entry(path: String, title: String, icon: String) {
        NavigationDrawerItem(
            label = { Text(title, style = HATextStyle.Body) },
            icon = { DashboardIcon(icon, colors.colorTextSecondary, Modifier.size(HASize.X2L)) },
            selected = path == selected,
            onClick = { onOpen("/$path") },
            colors = itemColors,
            modifier = Modifier.padding(horizontal = HADimens.SPACE2),
        )
    }
    // Material's phone drawer: the screen's width less a touch target for the scrim, at most 320dp
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val width = (screenWidth - DRAWER_SCRIM_GAP).coerceIn(DRAWER_MIN_WIDTH, DRAWER_MAX_WIDTH)
    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.width(width),
        // Square, as the frontend's sidebar, instead of Material's rounded open edge
        drawerShape = RectangleShape,
        drawerContainerColor = colors.colorSurfaceDefault,
    ) {
        SidebarHeader()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = HADimens.SPACE2),
        ) {
            PanelEntries(sidebar, onlyDashboards, onRetry) { Entry(it.urlPath, it.title, it.icon) }
        }
        HorizontalDivider()
        Column(modifier = Modifier.padding(vertical = HADimens.SPACE2)) {
            @Composable
            fun Action(label: String, icon: String, onClick: () -> Unit) = NavigationDrawerItem(
                label = { Text(label, style = HATextStyle.Body) },
                icon = { DashboardIcon(icon, colors.colorTextSecondary, Modifier.size(HASize.X2L)) },
                selected = false,
                onClick = onClick,
                colors = itemColors,
                modifier = Modifier.padding(horizontal = HADimens.SPACE2),
            )
            if (onOpenSettings != null) {
                Action(stringResource(R.string.native_dashboard_settings), SETTINGS_ICON, onOpenSettings)
            } else {
                if (sidebar.valueOrNull?.isAdmin == true) {
                    Entry(CONFIG_PANEL, stringResource(R.string.native_dashboard_settings), SETTINGS_ICON)
                }
                Entry(PROFILE_PANEL, stringResource(R.string.native_dashboard_profile), PROFILE_ICON)
            }
            onSwitchServer?.let { Action(stringResource(R.string.native_dashboard_switch_server), SERVER_ICON, it) }
        }
        Spacer(modifier = Modifier.padding(bottom = HADimens.SPACE2))
    }
}

/**
 * The panels: the native ones, then under their own heading those that open in the companion app (left out when
 * [onlyDashboards]); else that they load, or why they couldn't, with [onRetry].
 */
@Composable
private fun PanelEntries(
    sidebar: Loadable<SidebarState>,
    onlyDashboards: Boolean,
    onRetry: () -> Unit,
    entry: @Composable (SidebarItem) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    when (sidebar) {
        Loadable.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HALoading(Modifier.padding(HADimens.SPACE4))
        }
        is Loadable.Failed -> Column(
            modifier = Modifier.padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            Text(
                text = stringResource(
                    R.string.native_dashboard_load_failed,
                    LocalContext.current.loadErrorText(sidebar.error),
                ),
                style = HATextStyle.Body,
            )
            HAAccentButton(text = stringResource(R.string.native_dashboard_retry), onClick = onRetry)
        }
        is Loadable.Ready -> {
            sidebar.value.dashboardItems.forEach { entry(it) }
            // The pages that open in the companion app, apart under their own heading
            val elsewhere = sidebar.value.elsewhereItems.takeUnless { onlyDashboards }.orEmpty()
            if (elsewhere.isNotEmpty()) {
                Text(
                    stringResource(R.string.native_dashboard_elsewhere_heading),
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    modifier = Modifier.padding(
                        start = HADimens.SPACE6,
                        end = HADimens.SPACE6,
                        top = HADimens.SPACE4,
                        bottom = HADimens.SPACE2,
                    ),
                )
                elsewhere.forEach { entry(it) }
            }
        }
    }
}

/**
 * The sidebar's header on a phone (`ha-sidebar`'s `menu`): the app's name on a toolbar-high row, over a divider.
 */
// UPSTREAM-DIFF: the header names this app (HADash), where upstream's says "Home Assistant".
@Composable
private fun SidebarHeader() {
    Box(Modifier.fillMaxWidth().height(HEADER_HEIGHT), contentAlignment = Alignment.CenterStart) {
        Text(
            stringResource(R.string.native_dashboard_sidebar_title),
            style = HATextStyle.Body.copy(fontSize = HAFontSize.XL, textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = HEADER_PADDING),
        )
    }
    HorizontalDivider()
}

private const val CONFIG_PANEL = "config"
private const val PROFILE_PANEL = "profile"
private const val SETTINGS_ICON = "mdi:cog"
private const val PROFILE_ICON = "mdi:account"
private const val SERVER_ICON = "mdi:home-switch"

/** `--header-height`. */
private val HEADER_HEIGHT = 56.dp

/** The menu's 4px padding and the narrow title's 16px. */
private val HEADER_PADDING = 20.dp
private val DRAWER_SCRIM_GAP = 56.dp
private val DRAWER_MIN_WIDTH = 240.dp
private val DRAWER_MAX_WIDTH = 320.dp
