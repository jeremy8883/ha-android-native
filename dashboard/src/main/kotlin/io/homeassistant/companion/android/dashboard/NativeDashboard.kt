package io.homeassistant.companion.android.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import io.homeassistant.companion.android.dashboard.ui.DashboardScreen
import io.homeassistant.companion.android.dashboard.ui.DashboardViewModel

/**
 * The native dashboards with their navigation drawer, for the app to host as a destination.
 *
 * @param path the dashboard (and view) to show first, such as `/dashboard-test/kitchen`; `null` for the default
 * @param openDrawer whether to open the navigation drawer, as when the web frontend's menu button was pressed
 * @param onOpenWeb opens a path the native dashboards don't show (Settings, other panels) in the web frontend
 */
@Composable
fun NativeDashboard(path: String?, openDrawer: Boolean, onOpenWeb: (String) -> Unit) {
    val viewModel: DashboardViewModel = hiltViewModel()
    LaunchedEffect(viewModel, path) { if (path != null) viewModel.onOpenPath(path) }
    DashboardScreen(viewModel, onOpenWeb = onOpenWeb, openDrawer = openDrawer)
}
