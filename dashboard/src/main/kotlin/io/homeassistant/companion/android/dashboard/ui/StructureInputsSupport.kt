package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.strategy.CommonControls

/** Which of loading, kept, live or failed the states are, the changes that regenerate the structure. */
internal fun Loadable<*>.statesPhase(): Int = when (this) {
    Loadable.Loading -> PHASE_LOADING
    is Loadable.Ready -> if (keptAt == null) PHASE_LIVE else PHASE_KEPT
    is Loadable.Failed -> PHASE_FAILED
}

/**
 * The prediction as the section takes it: the last one loaded (kept through a failed refresh), or the failure
 * when none ever loaded; `null` while it loads.
 */
internal fun Loadable<List<String>>.asCommonControls(): CommonControls? = when (this) {
    Loadable.Loading -> null
    is Loadable.Ready -> CommonControls.Predicted(value)
    is Loadable.Failed -> CommonControls.Failed((error as? LoadError.Server)?.message ?: error.toString())
}

private const val PHASE_LOADING = 0
private const val PHASE_KEPT = 1
private const val PHASE_LIVE = 2
private const val PHASE_FAILED = 3
