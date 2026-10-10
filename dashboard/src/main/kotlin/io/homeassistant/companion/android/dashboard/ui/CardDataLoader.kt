package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.data.EnergyRepository
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.history.GraphHistory
import io.homeassistant.companion.android.dashboard.history.GraphHistoryKey
import io.homeassistant.companion.android.dashboard.history.historyStreamCommand
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Loads the data the shown cards ask for beyond the entities' states: energy collections and graph histories. */
class CardDataLoader @Inject internal constructor(
    energyRepository: EnergyRepository,
    private val historyRepository: HistoryRepository,
    private val clock: Clock,
) {
    /** The energy collections of the shown view, with the period each one's date selection chose. */
    internal val energy = EnergyCollections(energyRepository)

    /** The histories of [keys] the graphs draw, each once its stream answered. */
    fun graphHistories(keys: Set<GraphHistoryKey>): Flow<Map<GraphHistoryKey, GraphHistory>> = if (keys.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(keys.map(::graphHistory)) { histories -> histories.filterNotNull().toMap() }
    }

    /** [key]'s history as its graph subscribes to it (sensors' charts don't read attributes), `null` while loading. */
    private fun graphHistory(key: GraphHistoryKey): Flow<Pair<GraphHistoryKey, GraphHistory>?> =
        historyRepository.stream("graph history of ${key.entityId}", key.hoursToShow) {
            historyStreamCommand(key.entityId, withoutAttributes = true, clock.now().toJavaInstant(), key.hoursToShow)
        }.map { loadable ->
            when (loadable) {
                Loadable.Loading -> null
                // No recorded states is a loaded, empty history: the graph draws the current state alone
                is Loadable.Ready -> key to GraphHistory.Loaded(loadable.value[key.entityId].orEmpty())
                is Loadable.Failed ->
                    key to GraphHistory.Failed((loadable.error as? LoadError.Server)?.let { it.message ?: it.code })
            }
        }
}
