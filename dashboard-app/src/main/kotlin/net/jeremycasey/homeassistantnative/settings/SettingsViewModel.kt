package net.jeremycasey.homeassistantnative.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The app's own settings. */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
    /** Whether other pages open in the companion app; `null` until read. */
    val openOtherPages: StateFlow<Boolean?> = settings.openOtherPagesInCompanionApp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), null)

    fun onOpenOtherPagesChange(open: Boolean) {
        viewModelScope.launch { settings.setOpenOtherPagesInCompanionApp(open) }
    }
}
