package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The states of entities over time as the recorder's history stream sends them. Port of `HistoryStream` and its
// types (frontend@20260624.6 src/data/history.ts).

/**
 * An entity's state at a time of its history.
 *
 * @property attributes its attributes, `null` when the minimal response left them out (all but the first)
 * @property lastChanged when the state last changed (epoch seconds), `null` when it's [lastUpdated]
 * @property lastUpdated when the state object last changed (epoch seconds)
 */
data class HistoryState(
    val state: String,
    val attributes: JsonObject?,
    val lastChanged: Double?,
    val lastUpdated: Double,
) {
    /** When the state last changed, epoch seconds. */
    val changed: Double get() = lastChanged ?: lastUpdated
}

/** The history of each entity, by entity id, oldest first. */
typealias HistoryStates = Map<String, List<HistoryState>>

/** Read the `states` of a `history/stream` message; states without a time are left out. */
fun parseHistoryStates(states: JsonObject): HistoryStates = states.mapValues { (_, list) ->
    (list as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { row ->
        val lastUpdated = row.number("lu") ?: return@mapNotNull null
        HistoryState(
            state = row.string("s").orEmpty(),
            attributes = row["a"] as? JsonObject,
            lastChanged = row.number("lc"),
            lastUpdated = lastUpdated,
        )
    }
}

/**
 * The history after the stream's [message]: the first one is the history, later ones are added to it (sorted when
 * out of order), and with [purgeBefore] (epoch seconds) older states are dropped, keeping the state at that time.
 * Port of `HistoryStream.processMessage`.
 */
fun HistoryStates.merge(message: HistoryStates, purgeBefore: Double?): HistoryStates = when {
    isEmpty() -> message
    // Empty messages say no more history is coming
    message.isEmpty() -> this
    else -> (keys + message.keys.filterNot { it in this }).associateWith { id ->
        val combined = this[id]
        val added = message[id].orEmpty()
        when {
            combined == null -> added
            added.isEmpty() -> purge(combined, purgeBefore)
            // Sorted when out of order
            added.first().lastUpdated < combined.last().lastUpdated ->
                purge((combined + added).sortedBy { it.lastUpdated }, purgeBefore)
            else -> purge(combined + added, purgeBefore)
        }
    }
}

/** [states] without those before [before], the last of which is moved to [before] as the state then. */
private fun purge(states: List<HistoryState>, before: Double?): List<HistoryState> {
    val (expired, kept) = if (before ==
        null
    ) {
        emptyList<HistoryState>() to states
    } else {
        states.partition { it.lastUpdated < before }
    }
    val lastExpired = expired.lastOrNull()
    return when {
        before == null || lastExpired == null -> states
        kept.firstOrNull()?.lastUpdated == before -> kept
        else -> listOf(lastExpired.copy(lastUpdated = before, lastChanged = null)) + kept
    }
}
