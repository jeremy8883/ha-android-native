package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.components.UnsupportedMessage
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.views.DashboardView
import io.homeassistant.companion.android.dashboard.ui.panels.NativePanel
import io.homeassistant.companion.android.dashboard.ui.panels.PanelToolbar
import java.time.ZonedDateTime

/**
 * [content] with its saved state (scroll positions and the like) kept per page of [history]: a page back returns to
 * comes back as it was left, and a page opened anew starts fresh. Navigation Compose keeps its destinations' state
 * the same way, with a `SaveableStateHolder`. Without a [history] (nothing shown yet), nothing is kept.
 *
 * Upstream: none (native: Android restores a page's scroll position on back)
 */
@Composable
internal fun KeepPageStates(history: PageHistory?, content: @Composable () -> Unit) {
    val holder = rememberSaveableStateHolder()
    // The pages whose state the holder may have
    val kept = remember { mutableSetOf<String>() }
    LaunchedEffect(history) {
        val shown = history ?: return@LaunchedEffect
        val reachable = shown.reachable.mapTo(mutableSetOf(shown.current.key)) { it.key }
        (kept - reachable).forEach(holder::removeState)
        kept.retainAll(reachable)
    }
    val key = history?.current?.key
    if (key == null) {
        content()
    } else {
        kept += key
        holder.SaveableStateProvider(key, content)
    }
}

/**
 * The dashboard's view or the native panel shown, or why there is none.
 *
 * Upstream: `partial-panel-resolver` (src/layouts/partial-panel-resolver.ts)
 */
@Composable
internal fun DashboardPage(
    uiState: DashboardUiState,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    panelToolbar: PanelToolbar,
    onRetry: () -> Unit,
    onOpenElsewhere: (() -> Unit)?,
) {
    when (uiState) {
        DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HALoading() }
        DashboardUiState.NotFound -> Message(stringResource(R.string.native_dashboard_not_found))
        is DashboardUiState.Error -> LoadErrorScreen(uiState.error, onRetry)
        is DashboardUiState.UnsupportedStrategy -> UnsupportedMessage(
            uiState.title?.let { stringResource(R.string.native_dashboard_unsupported_strategy, it) }
                ?: stringResource(R.string.native_dashboard_unsupported_untitled),
            onOpenElsewhere,
        )
        is DashboardUiState.Content -> DashboardView(uiState, hass, now, interactions)
        is DashboardUiState.Panel -> NativePanel(uiState, panelToolbar, hass, now, interactions)
    }
}
