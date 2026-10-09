package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.CardPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The card choices kept on the device, by the frontend's local storage key, and how to change them. */
internal class CardPreferenceValues(val values: Map<String, String>, val set: (String, String) -> Unit)

/** Gives [content]'s cards the server's URL ([serverUrl], for its pictures) and the card choices kept on the device. */
@Composable
internal fun ProvideCardLocals(serverUrl: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalServerUrl provides serverUrl,
        LocalCardPreferences provides rememberCardPreferences(),
        content = content,
    )
}

/** The card choices kept on the device, for the cards to read and change. */
@Composable
private fun rememberCardPreferences(): CardPreferenceValues {
    val viewModel = hiltViewModel<CardPreferencesViewModel>()
    val values by viewModel.values.collectAsStateWithLifecycle()
    return remember(values) { CardPreferenceValues(values, viewModel::set) }
}

/** The card choices of the shown dashboard; none until they're loaded. */
internal val LocalCardPreferences = staticCompositionLocalOf { CardPreferenceValues(emptyMap()) { _, _ -> } }

/** Loads the card choices kept on the device and keeps them as they change. */
@HiltViewModel
internal class CardPreferencesViewModel @Inject constructor(private val preferences: CardPreferences) : ViewModel() {
    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The kept choices, by key. */
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    init {
        viewModelScope.launch { _values.update { preferences.load(CARD_PREFERENCE_KEYS) + it } }
    }

    /** Remembers [value] for [key]. */
    fun set(key: String, value: String) {
        _values.update { it + (key to value) }
        viewModelScope.launch { preferences.save(key, value) }
    }
}

/** The devices graph's chart type, `bar` or `pie`, for every devices graph. */
internal const val DEVICES_CHART_TYPE_KEY = "energy-devices-graph-chart-type"

private val CARD_PREFERENCE_KEYS = listOf(DEVICES_CHART_TYPE_KEY)
