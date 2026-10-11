package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.ui.logbook.parsePickedTarget
import java.time.Instant

// How the History and Logbook panels keep their filters across configuration changes.

/** Keeps the target, as its JSON. */
internal val TargetSaver = Saver<Target, String>(
    save = { it.toJson().toString() },
    restore = { parsePickedTarget(it) },
)

/** Keeps the period, as its ends' epoch milliseconds. */
internal val DateRangeSaver = listSaver<DateRange, Long>(
    save = { listOf(it.start.toEpochMilli(), it.end.toEpochMilli()) },
    restore = { DateRange(Instant.ofEpochMilli(it[0]), Instant.ofEpochMilli(it[1])) },
)
