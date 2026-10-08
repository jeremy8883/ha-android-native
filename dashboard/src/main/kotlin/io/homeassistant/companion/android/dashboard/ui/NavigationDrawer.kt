package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * The app's navigation drawer, in place of the frontend's sidebar: the panels in the user's order, then Settings
 * (for admins) and the profile, like `ha-sidebar`'s fixed entries.
 *
 * @param selected the url path of the panel shown now
 * @param onOpen called with the path of the chosen entry
 */
@Composable
internal fun NavigationDrawerContent(sidebar: SidebarState?, selected: String?, onOpen: (String) -> Unit) {
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
    ModalDrawerSheet(drawerContainerColor = colors.colorSurfaceDefault) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = HADimens.SPACE2),
        ) {
            sidebar?.items?.forEach { Entry(it.urlPath, it.title, it.icon) }
        }
        HorizontalDivider()
        Column(modifier = Modifier.padding(vertical = HADimens.SPACE2)) {
            if (sidebar?.isAdmin ==
                true
            ) {
                Entry(CONFIG_PANEL, stringResource(R.string.native_dashboard_settings), SETTINGS_ICON)
            }
            Entry(PROFILE_PANEL, stringResource(R.string.native_dashboard_profile), PROFILE_ICON)
        }
        Spacer(modifier = Modifier.padding(bottom = HADimens.SPACE2))
    }
}

private const val CONFIG_PANEL = "config"
private const val PROFILE_PANEL = "profile"
private const val SETTINGS_ICON = "mdi:cog"
private const val PROFILE_ICON = "mdi:account"
