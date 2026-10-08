package io.homeassistant.companion.android.dashboard

import androidx.compose.runtime.Composable

/**
 * The web frontend the app shows in place of the native dashboards for the other pages, under their navigation
 * drawer so the drawer opens over it.
 *
 * @property visible whether it shows now, instead of the native dashboards
 * @property panel the url path of the panel it shows, for the drawer to highlight
 * @property onShowDashboard hides it, as a native dashboard was opened
 * @property content the frontend
 */
class NativeDashboardWeb(
    val visible: Boolean,
    val panel: String?,
    val onShowDashboard: () -> Unit,
    val content: @Composable () -> Unit,
)
