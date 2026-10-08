package io.homeassistant.companion.android.dashboard

import io.homeassistant.companion.android.dashboard.data.DashboardRepository
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.ui.isNativeDashboard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tells which app paths the native dashboards show, for the app to hand the web frontend's route changes over to
 * them. The panels are fetched once and again when a path's panel is unknown (a dashboard added since).
 */
@Singleton
class NativeDashboardPaths @Inject constructor(private val repository: DashboardRepository) {
    private val mutex = Mutex()
    private var panels: Map<String, PanelInfo>? = null

    /**
     * Whether [path] (such as `/dashboard-test/kitchen?edit=1`) is a dashboard the native renderer shows. Dashboard
     * paths opened for editing (`edit=1`) stay in the web frontend, which has the editor.
     */
    suspend fun isNativeDashboard(path: String): Boolean {
        if (EDIT_PARAM.containsMatchIn(path.substringAfter('?', ""))) return false
        val urlPath = path.substringBefore('?').removePrefix("/").substringBefore('/')
        if (urlPath.isEmpty()) return false
        val panel = mutex.withLock {
            panels?.get(urlPath) ?: repository.serverInfo()?.panelInfo.also { panels = it }?.get(urlPath)
        }
        return panel != null && isNativeDashboard(panel)
    }

    /** Forget the panels, for example after switching servers. */
    suspend fun reset() = mutex.withLock { panels = null }

    private companion object {
        val EDIT_PARAM = Regex("(^|&)edit=1(&|$)")
    }
}
