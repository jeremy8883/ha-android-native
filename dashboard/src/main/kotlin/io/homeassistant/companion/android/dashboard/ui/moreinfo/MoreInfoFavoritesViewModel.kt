package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.EntityEntryRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** Why changing a light's favourites failed: saving them, or copying them to [failed] other lights. */
internal sealed interface FavoritesError {
    val error: LoadError

    data class Save(override val error: LoadError) : FavoritesError

    data class Copy(val failed: Int, override val error: LoadError) : FavoritesError
}

/**
 * An entity's registry entry, which holds its favourites (a light's colours, a cover's or valve's positions), while
 * its details are shown; the favourites' edit mode, shared by the header's menu and the favourites; and saving and
 * copying them. Port of what the more-info dialog does for them (frontend@20260624.6
 * src/dialogs/more-info/ha-more-info-dialog.ts).
 */
@HiltViewModel
internal class MoreInfoFavoritesViewModel @Inject constructor(private val repository: EntityEntryRepository) :
    ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    // The entry the server returned for the last save, which is newer than the one loaded
    private val saved = MutableStateFlow<JsonObject?>(null)

    private val _editMode = MutableStateFlow(false)
    private val _error = MutableStateFlow<FavoritesError?>(null)

    /** The entity's registry entry, `null` when it has none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entry: StateFlow<Loadable<JsonObject?>> = combine(
        entityId.filterNotNull().flatMapLatest(repository::entry),
        saved,
    ) { loaded, saved -> saved?.let { Loadable.Ready(it) } ?: loaded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Whether the favourites are being edited. */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /** The last change of the favourites that failed, until the next one. */
    val error: StateFlow<FavoritesError?> = _error.asStateFlow()

    /** Load [id]'s entry, unless it is the one loaded. */
    fun show(id: String) {
        if (entityId.value == id) return
        saved.value = null
        _editMode.value = false
        entityId.value = id
    }

    /** Start or stop editing the favourites. */
    fun setEditMode(editing: Boolean) {
        _editMode.value = editing
    }

    /** Send [command], an update of the entity's favourites; the entry the server returns is shown. */
    fun save(command: WsCommand) {
        val id = entityId.value ?: return
        _error.value = null
        viewModelScope.launch {
            when (val result = repository.update(command)) {
                is Fetched.Success -> saved.value = result.value
                is Fetched.Failure -> {
                    Timber.w("Couldn't save the favourites of $id: ${result.error}")
                    _error.value = FavoritesError.Save(result.error)
                }
            }
        }
    }

    /** Send [commands], copies of the favourites to other entities. */
    fun copy(commands: List<WsCommand>) {
        _error.value = null
        viewModelScope.launch {
            val failures = commands.map { command -> async { repository.update(command) } }
                .awaitAll()
                .filterIsInstance<Fetched.Failure>()
            failures.firstOrNull()?.let { first ->
                Timber.w("Couldn't copy favourites to ${failures.size} entities: ${first.error}")
                _error.value = FavoritesError.Copy(failures.size, first.error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
