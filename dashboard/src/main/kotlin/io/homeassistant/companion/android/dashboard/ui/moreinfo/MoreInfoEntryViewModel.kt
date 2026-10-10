package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.EntityEntryRepository
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * An entity's registry unique ID while its details show, as the dialog reads it from the entry (a script's action
 * is named by it); `null` while loading, when it has none, or when it couldn't be read (the details then use the
 * object ID, as upstream does without an entry).
 */
@HiltViewModel
internal class MoreInfoEntryViewModel @Inject constructor(repository: EntityEntryRepository) : ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uniqueId: StateFlow<String?> = entityId.filterNotNull().flatMapLatest(repository::entry)
        .map { loadable -> loadable.valueOrNull?.string("unique_id") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** Load [id]'s entry. */
    fun show(id: String) {
        entityId.value = id
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
