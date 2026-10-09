package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.EntityStatistics
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.history.HistoryStates
import io.homeassistant.companion.android.dashboard.history.historyStreamCommand
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the details show of an entity's history: its states, or its statistics. */
internal sealed interface EntityHistory {
    data class States(val history: HistoryStates) : EntityHistory

    data class Statistics(val statistics: EntityStatistics) : EntityHistory
}

/**
 * Which history to load: [entityId]'s statistics when [statistics] (falling back to its states when it has none),
 * else its states, [withoutAttributes] when its charts don't read them.
 */
internal data class HistoryRequest(val entityId: String, val statistics: Boolean, val withoutAttributes: Boolean)

/** Loads the history the details of an entity show, while they're shown. */
@HiltViewModel
internal class MoreInfoHistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val request = MutableStateFlow<HistoryRequest?>(null)

    /** The history of the requested entity. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<Loadable<EntityHistory>> = request.filterNotNull()
        .flatMapLatest { load(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loading)

    /** Load [historyRequest]'s history, unless it is the one loading. */
    fun show(historyRequest: HistoryRequest) {
        request.value = historyRequest
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun load(request: HistoryRequest): Flow<Loadable<EntityHistory>> = if (request.statistics) {
        // `null` once it turns out there are no statistics: then the states show, like the frontend's
        repository.statistics(request.entityId)
            .map { loadable -> loadable.takeUnless { it is Loadable.Ready && it.value == null } }
            .distinctUntilChanged { old, new -> old == null && new == null }
            .flatMapLatest { loadable -> loadable?.let { flowOf(it.toHistory()) } ?: states(request) }
    } else {
        states(request)
    }

    private fun Loadable<EntityStatistics?>.toHistory(): Loadable<EntityHistory> = when (this) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> this
        is Loadable.Ready -> value?.let {
            Loadable.Ready(EntityHistory.Statistics(it), refreshing, refreshError, keptAt)
        }
            ?: Loadable.Loading
    }

    private fun states(request: HistoryRequest): Flow<Loadable<EntityHistory>> =
        repository.stream("history of ${request.entityId}") {
            historyStreamCommand(request.entityId, request.withoutAttributes, clock.now().toJavaInstant())
        }.map { loadable -> loadable.map(EntityHistory::States) }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        val Loading: Loadable<EntityHistory> = Loadable.Loading
    }
}
