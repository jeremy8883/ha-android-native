package io.homeassistant.companion.android.dashboard.ui.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.ConfigEntriesRepository
import io.homeassistant.companion.android.dashboard.data.Loadable
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the target picker loads beyond the registries: each config entry's integration, to name and show devices'
 * integrations. Until loaded, or when it couldn't be (logged by the repository), devices show without one, as
 * upstream's picker does while its lookup is empty.
 */
@HiltViewModel
internal class TargetPickerViewModel @Inject constructor(repository: ConfigEntriesRepository) : ViewModel() {
    /** Each config entry's integration, by entry id. */
    val configEntryDomains: StateFlow<Map<String, String>> = repository.domains()
        .map { (it as? Loadable.Ready)?.value.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyMap())

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
