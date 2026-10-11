package io.homeassistant.companion.android.dashboard.ui.panels.history

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.dashboard.data.CardPreferences
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.energy.Statistics
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.HistoryResult
import io.homeassistant.companion.android.dashboard.history.HistoryStates
import io.homeassistant.companion.android.dashboard.history.computeHistory
import io.homeassistant.companion.android.dashboard.history.convertStatisticsToHistory
import io.homeassistant.companion.android.dashboard.history.historyCsv
import io.homeassistant.companion.android.dashboard.history.historyPanelStatisticsCommand
import io.homeassistant.companion.android.dashboard.history.historyRangeCommand
import io.homeassistant.companion.android.dashboard.history.mergeHistoryResults
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.ui.panels.logbook.parsePickedTarget
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Which history the History panel shows: [entityIds]' from [start] to [end], as [hass] knows them. */
internal data class HistoryPanelRequest(
    val entityIds: List<String>,
    val start: Instant,
    val end: Instant,
    val hass: HassSnapshot,
) {
    // The snapshot changes with every state; only what's asked for decides what loads
    override fun equals(other: Any?): Boolean =
        other is HistoryPanelRequest && other.entityIds == entityIds && other.start == start && other.end == end

    override fun hashCode(): Int = listOf(entityIds, start, end).hashCode()
}

/** What the History panel shows below its filters. */
internal sealed interface HistoryPanelState {
    /** Nothing is picked: it asks to pick something. */
    data object NoTarget : HistoryPanelState

    /** The history loads. */
    data object Loading : HistoryPanelState

    /** The history couldn't load. */
    data class Failed(val error: LoadError) : HistoryPanelState

    /** The charts' lines and timelines, with the statistics from before the states merged in. */
    data class Ready(val history: HistoryResult) : HistoryPanelState
}

/** How the download of the history as CSV went. */
internal sealed interface HistoryDownload {
    data object Idle : HistoryDownload

    data class Done(val failed: Boolean) : HistoryDownload
}

/**
 * Loads the History panel's history for a period: the recorder's history stream (kept up to date while the period
 * is ahead) and the hourly statistics from before it, merged as `ha-panel-history` does; and remembers its picked
 * target as the frontend does.
 */
@HiltViewModel
internal class HistoryPanelViewModel @VisibleForTesting constructor(
    private val repository: HistoryRepository,
    private val preferences: CardPreferences,
    private val context: Context,
    private val default: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        repository: HistoryRepository,
        preferences: CardPreferences,
        @ApplicationContext context: Context,
    ) : this(repository, preferences, context, Dispatchers.Default, Dispatchers.IO)

    private val request = MutableStateFlow<HistoryPanelRequest?>(null)

    /** The history of the requested entities and period. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<HistoryPanelState> = request.filterNotNull()
        .flatMapLatest { load(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HistoryPanelState.Loading)

    private val _download = MutableStateFlow<HistoryDownload>(HistoryDownload.Idle)

    /** How the last download went, until [onDownloadShown]. */
    val download: StateFlow<HistoryDownload> = _download.asStateFlow()

    /** Load [historyRequest]'s history, unless it is the one loaded. */
    fun show(historyRequest: HistoryPanelRequest) {
        request.value = historyRequest
    }

    /** The target last picked on the panel (`historyPickedValue`); `null` when none was. */
    suspend fun pickedTarget(): Target? = preferences.load(listOf(PICKED_KEY))[PICKED_KEY]?.let(::parsePickedTarget)

    /** Remembers [target] as the panel's picked target. */
    fun savePickedTarget(target: Target) {
        viewModelScope.launch { preferences.save(PICKED_KEY, target.toJson().toString()) }
    }

    /** Saves [history] as CSV to [target], a document the user picked. Port of `_downloadHistory`. */
    fun downloadCsv(history: HistoryResult, target: Uri) {
        viewModelScope.launch {
            val saved = withContext(io) {
                try {
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(historyCsv(history).toByteArray())
                    } !=
                        null
                } catch (e: IOException) {
                    Timber.w(e, "Couldn't save the history to $target")
                    false
                }
            }
            _download.value = HistoryDownload.Done(failed = !saved)
        }
    }

    /** The download's outcome was shown. */
    fun onDownloadShown() {
        _download.value = HistoryDownload.Idle
    }

    private fun load(request: HistoryPanelRequest): Flow<HistoryPanelState> {
        if (request.entityIds.isEmpty()) return flowOf(HistoryPanelState.NoTarget)
        val states = repository.stream("history of ${request.entityIds.size} entities", hours = null) {
            request.hass.historyRangeCommand(request.entityIds, request.start, request.end)
        }
        // A failure leaves the states alone in the charts, as upstream's does
        val statistics = repository.periodStatistics(
            historyPanelStatisticsCommand(request.entityIds, request.start, request.end),
        ).map { it.valueOrNull }.onStart { emit(null) }
        return combine(states, statistics) { loaded, stats -> loaded to stats }
            .map { (loaded, stats) -> panelState(request, loaded, stats) }
            .flowOn(default)
    }

    private fun panelState(
        request: HistoryPanelRequest,
        loaded: Loadable<HistoryStates>,
        statistics: Statistics?,
    ): HistoryPanelState = when (loaded) {
        Loadable.Loading -> HistoryPanelState.Loading
        is Loadable.Failed -> HistoryPanelState.Failed(loaded.error)
        is Loadable.Ready -> {
            val hass = request.hass
            val states = hass.computeHistory(loaded.value, request.entityIds, splitDeviceClasses = true)
            val lts = statistics?.let {
                hass.convertStatisticsToHistory(it, request.entityIds, splitDeviceClasses = true)
            }
            HistoryPanelState.Ready(mergeHistoryResults(states, lts))
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val PICKED_KEY = "historyPickedValue"
    }
}
