package net.jeremycasey.homeassistantnative.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.ShortcutPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Whether keyboard shortcuts are on, as the profile's "Keyboard shortcuts" in the frontend. */
@HiltViewModel
internal class KeyboardShortcutsSettingViewModel @Inject constructor(private val preferences: ShortcutPreferences) :
    ViewModel() {
    /** Whether keyboard shortcuts are on. */
    val enabled: StateFlow<Boolean> = preferences.enabled

    /** Turns keyboard shortcuts on or off. */
    fun onEnabledChange(enabled: Boolean) = preferences.setEnabled(enabled)
}
