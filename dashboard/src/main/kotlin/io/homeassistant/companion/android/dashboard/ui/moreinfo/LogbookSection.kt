package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.logbook.LogbookRow

/** What the logbook section shows: its rows once loaded, and whether its times read as how long ago. */
internal class LogbookSection(
    val entityId: String,
    val logbook: Loadable<List<LogbookRow>>,
    relative: MutableState<Boolean>,
) {
    /** Whether every time shows as how long ago it was, rather than the time. */
    var relative by relative
}
