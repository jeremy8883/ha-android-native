package io.homeassistant.companion.android.nativedashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendCallbacks

/**
 * Show the native dashboards: back to them when they are on the back stack (the frontend was opened from there),
 * otherwise on top.
 */
internal fun NavController.navigateToNativeDashboard(path: String? = null, openDrawer: Boolean = false) {
    navigate(NativeDashboardRoute(path, openDrawer)) {
        popUpTo<NativeDashboardRoute> { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Registers the native dashboards destination, with the web frontend in it for the pages they don't show (see
 * [NativeDashboardShell]).
 */
internal fun NavGraphBuilder.nativeDashboardScreen(frontendCallbacks: FrontendCallbacks) {
    composable<NativeDashboardRoute> { entry ->
        NativeDashboardShell(
            route = entry.toRoute<NativeDashboardRoute>(),
            frontendCallbacks = frontendCallbacks,
            onShowServerSwitcher = frontendCallbacks::onShowServerSwitcher,
        )
    }
}
