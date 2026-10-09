package io.homeassistant.companion.android.dashboard.ui

// Where the dashboard screen is and where back leads, apart from the view model's flows

/** A dashboard ([dashboard] `null` for the default one) with its opened views. */
internal data class Location(val dashboard: String?, val stack: List<String>)

/** The opened views, whether other pages can be opened, and where back returns after the first view. */
internal data class Navigation(
    val stack: List<String>,
    val canOpenOtherPages: Boolean,
    val returnPoints: List<Location>,
)

/** Whether this path's query asks for back to return to the page that opened it, as the frontend's panels read it. */
internal fun String.hasHistoryBack(): Boolean =
    substringAfter('?', "").split('&').any { it.substringBefore('=') == HISTORY_BACK_PARAM }

/** This, with a back button to the page that opened it when [canReturn] and on its first view. */
internal fun DashboardUiState.withReturn(canReturn: Boolean): DashboardUiState =
    if (canReturn && this is DashboardUiState.Content && !isSubview) copy(canGoBack = true) else this

private const val HISTORY_BACK_PARAM = "historyBack"
