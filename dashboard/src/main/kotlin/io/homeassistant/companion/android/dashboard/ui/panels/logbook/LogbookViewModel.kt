package io.homeassistant.companion.android.dashboard.ui.panels.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.CardPreferences
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.LogbookRepository
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.logbook.LogbookEntry
import io.homeassistant.companion.android.dashboard.logbook.TraceContext
import io.homeassistant.companion.android.dashboard.target.Target
import java.time.Instant
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A logbook: its [entries], newest first, with the [users] list (`null` when
 * it isn't known: for users other than admins, or while it loads) and the runs' [traces] (`null` while not known,
 * which leaves the trace links out).
 */
internal data class LoadedLogbook(
    val entries: List<LogbookEntry>,
    val users: JsonArray?,
    val traces: Map<String, TraceContext>?,
)

/** Which entries a logbook lists. */
internal sealed interface LogbookSource {
    /** [entityId]'s over the last day, as its details list them. */
    data class Entity(val entityId: String) : LogbookSource

    /** Those from [start] to [end] of [entityIds] (every entity's when `null`), as the Logbook panel lists them. */
    data class Range(val start: Instant, val end: Instant, val entityIds: List<String>?) : LogbookSource
}

/**
 * Which logbook to load: [source]'s entries, with what only admins may read when [isAdmin]. A new [generation]
 * loads it again, as the panel's refresh does.
 */
internal data class LogbookRequest(val source: LogbookSource, val isAdmin: Boolean, val generation: Int = 0)

/** Loads a logbook (an entity's details' or the Logbook panel's) while it is shown. */
@HiltViewModel
internal class LogbookViewModel @Inject constructor(
    private val repository: LogbookRepository,
    private val preferences: CardPreferences,
) : ViewModel() {
    private val request = MutableStateFlow<LogbookRequest?>(null)

    /** The requested logbook. The users and traces fill in as they load; without them rows name less. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val logbook: StateFlow<Loadable<LoadedLogbook>> = request.filterNotNull()
        .flatMapLatest { load(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loading)

    /** Load [logbookRequest]'s logbook, unless it is the one loading. */
    fun show(logbookRequest: LogbookRequest) {
        request.value = logbookRequest
    }

    /**
     * The target last picked on the Logbook panel, kept on the device as the frontend keeps it in the browser
     * (`logbookPickedValue`); `null` when none was.
     */
    suspend fun pickedTarget(): Target? = preferences.load(listOf(PICKED_KEY))[PICKED_KEY]?.let(::parsePickedTarget)

    /** Remembers [target] as the Logbook panel's picked target. */
    fun savePickedTarget(target: Target) {
        viewModelScope.launch { preferences.save(PICKED_KEY, target.toJson().toString()) }
    }

    private fun load(request: LogbookRequest): Flow<Loadable<LoadedLogbook>> = combine(
        when (val source = request.source) {
            is LogbookSource.Entity -> repository.entries(source.entityId)
            is LogbookSource.Range -> repository.entries(source.start, source.end, source.entityIds)
        },
        adminOnly(request.isAdmin) { repository.users() },
        adminOnly(request.isAdmin) { repository.traceContexts() },
    ) { entries, users, traces ->
        entries.map { LoadedLogbook(it, users.valueOrNull, traces.valueOrNull) }
    }

    private fun <T> adminOnly(isAdmin: Boolean, load: () -> Flow<Loadable<T>>): Flow<Loadable<T>> =
        if (isAdmin) load().onStart { emit(Loadable.Loading) } else flowOf(Loadable.Loading)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val PICKED_KEY = "logbookPickedValue"
        val Loading: Loadable<LoadedLogbook> = Loadable.Loading
    }
}

/** The picked target as the frontend stores it (`HassServiceTarget` JSON); `null` when unreadable. */
internal fun parsePickedTarget(json: String): Target? =
    (runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject)?.let(Target::fromJson)
