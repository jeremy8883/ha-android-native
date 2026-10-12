package io.homeassistant.companion.android.dashboard.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether keyboard shortcuts are on: the frontend's `enableShortcuts`, which it keeps in the browser's local storage
 * (`ha-pref-storage`), on by default; kept here with the cards' choices, under the same key.
 */
@Singleton
class ShortcutPreferences internal constructor(
    private val preferences: CardPreferences,
    private val scope: CoroutineScope,
) {
    // The scope lives as long as the app, as this singleton
    @Inject
    constructor(preferences: CardPreferences) : this(preferences, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val _enabled = MutableStateFlow(true)

    /** Whether keyboard shortcuts are on; on until the saved choice is read, as upstream defaults to. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        scope.launch {
            preferences.load(listOf(KEY))[KEY]?.let { saved -> _enabled.value = saved != false.toString() }
        }
    }

    /** Turns keyboard shortcuts on or off, and remembers it. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        scope.launch { preferences.save(KEY, enabled.toString()) }
    }

    private companion object {
        const val KEY = "enableShortcuts"
    }
}
