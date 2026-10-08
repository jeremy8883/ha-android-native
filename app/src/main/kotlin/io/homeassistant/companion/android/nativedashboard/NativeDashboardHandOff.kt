package io.homeassistant.companion.android.nativedashboard

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
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
 * frontend's own back, or the default panel after a redirect.
 *
 * @param frontendRoute the URL the frontend is at, following its route changes
 * @param onNativeDashboard shows the native dashboards at the path the frontend went to
 */
@Composable
internal fun NativeDashboardHandOff(frontendRoute: StateFlow<String?>, onNativeDashboard: (path: String) -> Unit) {
    val viewModel: NativeDashboardHandOffViewModel = hiltViewModel()
    val currentOnNativeDashboard by rememberUpdatedState(onNativeDashboard)
    LaunchedEffect(frontendRoute) {
        frontendRoute.filterNotNull()
            .map { url -> Uri.parse(url).let { uri -> uri.path.orEmpty() + (uri.query?.let { "?$it" } ?: "") } }
            .distinctUntilChanged()
            .collect { path ->
                if (viewModel.paths.isNativeDashboard(path)) currentOnNativeDashboard(path)
            }
    }
}
