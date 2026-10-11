package io.homeassistant.companion.android.dashboard.ui

// Where the dashboard screen is and where back leads, apart from the view model's flows

/** A dashboard ([dashboard] `null` for the default one) with its opened views. */
internal data class Location(val dashboard: String?, val stack: List<String>)

/**
 * The opened views, whether other pages can be opened, where back returns after the first view, and the query a
 * native panel was opened with.
 */
internal data class Navigation(
    val stack: List<String>,
    val canOpenOtherPages: Boolean,
    val returnPoints: List<Location>,
    val panelQuery: String?,
)

/**
 * Whether this path's query asks for back to return to the page that opened it, as the frontend's panels read it:
 * the dashboards' `historyBack`, the History and Logbook panels' `back=1`.
 */
internal fun String.hasHistoryBack(): Boolean = substringAfter('?', "").split('&').any { param ->
    param.substringBefore('=') == HISTORY_BACK_PARAM || param == PANEL_BACK_PARAM
}

/** This, with a back button to the page that opened it when [canReturn] and on its first view. */
internal fun DashboardUiState.withReturn(canReturn: Boolean): DashboardUiState = when {
    !canReturn -> this
    this is DashboardUiState.Content && !isSubview -> copy(canGoBack = true)
    this is DashboardUiState.Panel -> copy(canGoBack = true)
    else -> this
}

/** This native panel opened with [query]. */
internal fun DashboardUiState.withPanelQuery(query: String?): DashboardUiState =
    if (this is DashboardUiState.Panel) copy(query = query) else this

private const val HISTORY_BACK_PARAM = "historyBack"
private const val PANEL_BACK_PARAM = "back=1"
