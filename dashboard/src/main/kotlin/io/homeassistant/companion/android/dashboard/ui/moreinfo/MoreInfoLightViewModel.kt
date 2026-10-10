package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.EntityEntryRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.moreinfo.LightColor
import io.homeassistant.companion.android.dashboard.moreinfo.favoriteColorsUpdate
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
 * A light's registry entry, which holds its favourite colours, while its details are shown; the favourites' edit
 * mode, shared by the header's menu and the favourites; and the saving and copying of favourites. Port of what the
 * more-info dialog does for them (frontend@20260624.6 src/dialogs/more-info/ha-more-info-dialog.ts).
 */
@HiltViewModel
internal class MoreInfoLightViewModel @Inject constructor(private val repository: EntityEntryRepository) :
    ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    // The entry the server returned for the last save, which is newer than the one loaded
    private val saved = MutableStateFlow<JsonObject?>(null)

    private val _editMode = MutableStateFlow(false)
    private val _error = MutableStateFlow<FavoritesError?>(null)

    /** The light's registry entry, `null` when it has none. */
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

    /** Load [lightId]'s entry, unless it is the one loaded. */
    fun show(lightId: String) {
        if (entityId.value == lightId) return
        saved.value = null
        _editMode.value = false
        entityId.value = lightId
    }

    /** Start or stop editing the favourites. */
    fun setEditMode(editing: Boolean) {
        _editMode.value = editing
    }

    /** Save [colors] as the light's favourites, or reset them to the defaults when `null`. */
    fun save(colors: List<LightColor>?) {
        val lightId = entityId.value ?: return
        _error.value = null
        viewModelScope.launch {
            when (val result = repository.update(favoriteColorsUpdate(lightId, colors))) {
                is Fetched.Success -> saved.value = result.value
                is Fetched.Failure -> {
                    Timber.w("Couldn't save the favourite colours of $lightId: ${result.error}")
                    _error.value = FavoritesError.Save(result.error)
                }
            }
        }
    }

    /** Save [colors] as the favourites of each of [lightIds]. */
    fun copy(colors: List<LightColor>, lightIds: List<String>) {
        _error.value = null
        viewModelScope.launch {
            val failures = lightIds.map { id -> async { repository.update(favoriteColorsUpdate(id, colors)) } }
                .awaitAll()
                .filterIsInstance<Fetched.Failure>()
            failures.firstOrNull()?.let { first ->
                Timber.w("Couldn't copy favourite colours to ${failures.size} lights: ${first.error}")
                _error.value = FavoritesError.Copy(failures.size, first.error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
