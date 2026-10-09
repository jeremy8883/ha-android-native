package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * The app's navigation drawer, in place of the frontend's sidebar: the panels in the user's order, then Settings
 * (for admins) and the profile, like `ha-sidebar`'s fixed entries.
 *
 * @param selected the url path of the panel shown now
 * @param drawerState the drawer's state, which back closes (with Material's predictive back animation)
 * @param sidebar the panels, or why they couldn't be loaded, with [onRetry] to try again
 * @param onSwitchServer shows the server picker; `null` hides the entry (a single server)
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
        drawerContainerColor = colors.colorSurfaceDefault,
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = HADimens.SPACE2),
        ) {
            when (sidebar) {
                Loadable.Loading -> HALoading(Modifier.align(Alignment.CenterHorizontally).padding(HADimens.SPACE4))
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
                is Loadable.Ready -> sidebar.value.items.forEach { Entry(it.urlPath, it.title, it.icon) }
            }
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

private const val CONFIG_PANEL = "config"
private const val PROFILE_PANEL = "profile"
private const val SETTINGS_ICON = "mdi:cog"
private const val PROFILE_ICON = "mdi:account"
private const val SERVER_ICON = "mdi:home-switch"

private val DRAWER_SCRIM_GAP = 56.dp
private val DRAWER_MIN_WIDTH = 240.dp
private val DRAWER_MAX_WIDTH = 320.dp
