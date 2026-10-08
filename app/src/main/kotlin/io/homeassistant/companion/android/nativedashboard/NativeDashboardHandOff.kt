package io.homeassistant.companion.android.nativedashboard

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.NativeDashboardPaths
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** Exposes [NativeDashboardPaths] to the frontend destination. */
@HiltViewModel
internal class NativeDashboardHandOffViewModel @Inject constructor(val paths: NativeDashboardPaths) : ViewModel()

/**
 * Hands the web frontend over to the native dashboards when it navigates to a dashboard they show: a link, the
 * frontend's own back, or the default panel after a redirect. The frontend destination stays below, so its
 * history is kept for the next panel opened in it.
 *
 * @param frontendRoute the URL the frontend is at, following its route changes
 */
@Composable
internal fun NativeDashboardHandOff(frontendRoute: StateFlow<String?>, navController: NavController) {
    val viewModel: NativeDashboardHandOffViewModel = hiltViewModel()
    LaunchedEffect(frontendRoute) {
        frontendRoute.filterNotNull()
            .map { url -> Uri.parse(url).let { uri -> uri.path.orEmpty() + (uri.query?.let { "?$it" } ?: "") } }
            .distinctUntilChanged()
            .collect { path ->
                if (viewModel.paths.isNativeDashboard(path)) navController.navigateToNativeDashboard(path)
            }
    }
}
