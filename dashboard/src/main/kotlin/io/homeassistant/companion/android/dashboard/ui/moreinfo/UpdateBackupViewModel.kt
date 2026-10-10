package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.UpdateBackupRepository
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateBackupSettings
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateType
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** The backup settings an update's backup switch reads, while its details are shown. */
@HiltViewModel
internal class UpdateBackupViewModel @Inject constructor(repository: UpdateBackupRepository) : ViewModel() {
    private val request = MutableStateFlow<Pair<UpdateType, Set<String>>?>(null)

    /** The settings, as far as read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<Loadable<UpdateBackupSettings>> = request.filterNotNull()
        .flatMapLatest { (type, components) -> repository.settings(type, components) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Reads the settings of [type]'s switch on a server with [components]. */
    fun show(type: UpdateType, components: Set<String>) {
        request.value = type to components
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
