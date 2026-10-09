package io.homeassistant.companion.android.dashboard

import io.homeassistant.companion.android.dashboard.data.ActiveServerRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.ui.isNativeDashboard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Tells which app paths the native dashboards show, for the app to hand the web frontend's route changes over to
 * them. The panels are fetched once and again when a path's panel is unknown (a dashboard added since).
 */
@Singleton
class NativeDashboardPaths @Inject constructor(private val repository: ActiveServerRepository) {
    private val mutex = Mutex()
    private var panels: Map<String, PanelInfo>? = null

    /**
     * Whether [path] (such as `/dashboard-test/kitchen?edit=1`) is a dashboard the native renderer shows. Dashboard
     * paths opened for editing (`edit=1`) stay in the web frontend, which has the editor, and so do those showing the
     * frontend's more-info dialog (`more-info-entity-id`) until it is closed.
     */
    suspend fun isNativeDashboard(path: String): Boolean {
        val query = path.substringAfter('?', "")
        val urlPath = path.substringBefore('?').removePrefix("/").substringBefore('/')
        val staysInWeb =
            EDIT_PARAM.containsMatchIn(query) || MORE_INFO_PARAM.containsMatchIn(query) || urlPath.isEmpty()
        return !staysInWeb && panel(urlPath)?.let(::isNativeDashboard) == true
    }

    /** The panel at [urlPath], loading the panels when it is unknown; `null` when there is none or they can't load. */
    private suspend fun panel(urlPath: String): PanelInfo? = mutex.withLock {
        panels?.get(urlPath) ?: when (val loaded = repository.panels()) {
            is Fetched.Success -> loaded.value.also { panels = it }[urlPath]
            is Fetched.Failure -> {
                // Unknown, so the path stays in the web frontend, which can show it
                Timber.w("Failed to load the panels to tell whether /$urlPath is a dashboard: ${loaded.error}")
                null
            }
        }
    }

    /** Forget the panels, for example after switching servers. */
    suspend fun reset() = mutex.withLock { panels = null }

    private companion object {
        val EDIT_PARAM = Regex("(^|&)edit=1(&|$)")
        val MORE_INFO_PARAM = Regex("(^|&)more-info-entity-id=")
    }
}
