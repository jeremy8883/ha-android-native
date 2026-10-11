package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.panels.PanelActions
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbarActions

/**
 * The toolbar: the dashboard's or panel's title, back to the page that opened it (else the menu), and the buttons a
 * native panel put in it ([panelActions]).
 */
@Composable
internal fun DashboardTopBar(
    uiState: DashboardUiState,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    panelActions: PanelActions,
) {
    val content = uiState as? DashboardUiState.Content
    val panel = uiState as? DashboardUiState.Panel
    val goesBack = content?.showsBack == true || panel?.canGoBack == true
    HATopBar(
        title = {
            Text(content?.title ?: panel?.title ?: (uiState as? DashboardUiState.UnsupportedStrategy)?.title.orEmpty())
        },
        navigationIcon = {
            if (goesBack) {
                ToolbarIconButton(ToolbarIcons.BACK, stringResource(commonR.string.navigate_up), onBack)
            } else {
                ToolbarIconButton(ToolbarIcons.MENU, stringResource(R.string.native_dashboard_menu), onOpenMenu)
            }
        },
        actions = { PanelToolbarActions(panelActions) },
    )
}

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
}
