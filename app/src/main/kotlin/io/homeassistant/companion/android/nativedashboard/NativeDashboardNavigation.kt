package io.homeassistant.companion.android.nativedashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendCallbacks
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import kotlinx.serialization.Serializable

/**
 * The native dashboards (`:dashboard`), shown instead of the web frontend for dashboards when
 * [io.homeassistant.companion.android.WIPFeature.USE_NATIVE_DASHBOARD] is on.
 *
 * @property path the dashboard (and view) to show, `null` for the default panel
 * @property openDrawer open the navigation drawer, as when the frontend's menu button was pressed
 */
@Serializable
internal data class NativeDashboardRoute(
    val path: String? = null,
    val openDrawer: Boolean = false,
    val moreInfoEntityId: String? = null,
) : HAStartDestinationRoute {
    companion object {
        /** The native dashboards opened for a frontend [target]: its path, or its entity's more-info. */
        fun from(target: FrontendTarget): NativeDashboardRoute = when (target) {
            FrontendTarget.Default -> NativeDashboardRoute()
            is FrontendTarget.Path -> NativeDashboardRoute(path = target.path)
            is FrontendTarget.EntityMoreInfo -> NativeDashboardRoute(moreInfoEntityId = target.entityId)
        }
    }
}

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
            onShowServerSwitcher = frontendCallbacks.onShowServerSwitcher,
        )
    }
}
