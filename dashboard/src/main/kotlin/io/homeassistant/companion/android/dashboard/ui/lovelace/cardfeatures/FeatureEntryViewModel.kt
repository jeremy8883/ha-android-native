package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.EntityEntryRepository
import io.homeassistant.companion.android.dashboard.data.Loadable
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject

/**
 * The registry entry of a tile's entity, which holds the favourites its favourite features show, as those
 * features load it (frontend@20260624.6 src/panels/lovelace/card-features/hui-numeric-favorite-card-feature-base.ts,
 * hui-light-color-favorites-card-feature.ts).
 */
@HiltViewModel
internal class FeatureEntryViewModel @Inject constructor(repository: EntityEntryRepository) : ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    /** The entity's registry entry, `null` when it has none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entry: StateFlow<Loadable<JsonObject?>> = entityId.filterNotNull().flatMapLatest(repository::entry)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Load [id]'s entry. */
    fun show(id: String) {
        entityId.value = id
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
