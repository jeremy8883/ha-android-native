package io.homeassistant.companion.android.dashboard

/**
 * What the native dashboards are asked to show. Each request is applied once, so a later one with another [id]
 * is applied again even when it asks for the same, such as opening the drawer a second time.
 *
 * @property path the dashboard (and view) to show, such as `/dashboard-test/kitchen`; `null` keeps the current one
 * @property openDrawer whether to open the navigation drawer, as when the web frontend's menu button was pressed
 * @property moreInfoEntityId an entity to show the more-info of, as a `more-info-entity-id` link asks
 * @property id tells requests apart
 */
data class NativeDashboardRequest(
    val path: String? = null,
    val openDrawer: Boolean = false,
    val moreInfoEntityId: String? = null,
    val id: Int = 0,
)
