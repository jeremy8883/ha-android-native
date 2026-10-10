package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.data.EnergyRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.ForecastRepository
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.ServerActionsRepository
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.history.GraphHistory
import io.homeassistant.companion.android.dashboard.history.GraphHistoryKey
import io.homeassistant.companion.android.dashboard.history.historyStreamCommand
import io.homeassistant.companion.android.dashboard.weather.ForecastEvent
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Loads the data the shown cards ask for beyond the entities' states: energy collections, graph histories, weather
 * forecasts, and whether alarm panels have a default code.
 */
class CardDataLoader @Inject internal constructor(
    energyRepository: EnergyRepository,
    private val historyRepository: HistoryRepository,
    private val serverActions: ServerActionsRepository,
    private val forecastRepository: ForecastRepository,
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

    /**
     * Whether each alarm panel of [entityIds] stores a default code, read from its registry entry as the alarm
     * panel card does. One that couldn't be read is left out, so its card asks for the code, as upstream's does.
     */
    fun alarmDefaultCodes(entityIds: Set<String>): Flow<Map<String, Boolean>> = flow {
        val known = mutableMapOf<String, Boolean>()
        emit(known.toMap())
        entityIds.forEach { entityId ->
            when (val code = serverActions.defaultCode(entityId, ALARM_DOMAIN)) {
                is Fetched.Success -> known[entityId] = code.value != null
                is Fetched.Failure -> Timber.w("Couldn't read whether $entityId has a default code: ${code.error}")
            }
            emit(known.toMap())
        }
    }

    /**
     * The forecasts of [keys], each once the server sent it. One that couldn't be subscribed to is left out (and
     * logged), so its card shows the current weather alone, as upstream's does when its subscription fails.
     */
    fun forecasts(keys: Set<ForecastKey>): Flow<Map<ForecastKey, ForecastEvent>> = if (keys.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(
            keys.map { key ->
                forecastRepository.forecast(key).map { loadable ->
                    if (loadable is Loadable.Failed) {
                        Timber.w(
                            "Couldn't subscribe to the $key forecast: ${loadable.error}",
                        )
                    }
                    loadable.valueOrNull?.let { key to it }
                }
            },
        ) { forecasts -> forecasts.filterNotNull().toMap() }
    }

    private companion object {
        const val ALARM_DOMAIN = "alarm_control_panel"
    }
}
