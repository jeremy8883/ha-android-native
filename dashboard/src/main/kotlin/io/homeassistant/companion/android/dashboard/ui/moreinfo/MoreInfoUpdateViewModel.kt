package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.ReleaseNotesRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** An update's release notes while its details are shown. */
@HiltViewModel
internal class MoreInfoUpdateViewModel @Inject constructor(repository: ReleaseNotesRepository) : ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    /** The release notes, `null` when the update has none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val releaseNotes: StateFlow<Loadable<String?>> = entityId.filterNotNull()
        .flatMapLatest(repository::releaseNotes)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Loads the notes of [id]. */
    fun show(id: String) {
        entityId.value = id
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
