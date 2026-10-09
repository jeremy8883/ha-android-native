package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.LogbookRepository
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.logbook.LogbookEntry
import io.homeassistant.companion.android.dashboard.logbook.TraceContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonArray

/**
 * What the details show of an entity's logbook: its [entries], newest first, with the [users] list (`null` when
 * it isn't known: for users other than admins, or while it loads) and the runs' [traces].
 */
internal data class EntityLogbook(
    val entries: List<LogbookEntry>,
    val users: JsonArray?,
    val traces: Map<String, TraceContext>,
)

/** Which logbook to load: [entityId]'s, with what only admins may read when [isAdmin]. */
internal data class LogbookRequest(val entityId: String, val isAdmin: Boolean)

/** Loads the logbook the details of an entity show, while they're shown. */
@HiltViewModel
internal class MoreInfoLogbookViewModel @Inject constructor(private val repository: LogbookRepository) :
    ViewModel() {
    private val request = MutableStateFlow<LogbookRequest?>(null)

    /** The logbook of the requested entity. The users and traces fill in as they load; without them rows name less. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val logbook: StateFlow<Loadable<EntityLogbook>> = request.filterNotNull()
        .flatMapLatest { load(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loading)

    /** Load [logbookRequest]'s logbook, unless it is the one loading. */
    fun show(logbookRequest: LogbookRequest) {
        request.value = logbookRequest
    }

    private fun load(request: LogbookRequest): Flow<Loadable<EntityLogbook>> = combine(
        repository.entries(request.entityId),
        adminOnly(request.isAdmin) { repository.users() },
        adminOnly(request.isAdmin) { repository.traceContexts() },
    ) { entries, users, traces ->
        entries.map { EntityLogbook(it, users.valueOrNull, traces.valueOrNull.orEmpty()) }
    }

    private fun <T> adminOnly(isAdmin: Boolean, load: () -> Flow<Loadable<T>>): Flow<Loadable<T>> =
        if (isAdmin) load().onStart { emit(Loadable.Loading) } else flowOf(Loadable.Loading)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        val Loading: Loadable<EntityLogbook> = Loadable.Loading
    }
}
