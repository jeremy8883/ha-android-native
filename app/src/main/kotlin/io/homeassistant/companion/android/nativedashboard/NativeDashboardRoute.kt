package io.homeassistant.companion.android.nativedashboard

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
