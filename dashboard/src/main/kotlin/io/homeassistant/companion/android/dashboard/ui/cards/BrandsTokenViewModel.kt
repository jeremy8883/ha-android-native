package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.BrandsRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * The brands API's token for the cards' brand images, fetched again every half hour as the server rotates it. A
 * failed fetch is logged and tried again a minute later, the last token kept meanwhile.
 */
@HiltViewModel
internal class BrandsTokenViewModel @Inject constructor(repository: BrandsRepository) : ViewModel() {
    /** The current token, `null` until one was fetched. */
    val token: StateFlow<String?> = flow {
        while (true) {
            val fetched = when (val result = repository.token()) {
                is Fetched.Success -> true.also { emit(result.value) }
                is Fetched.Failure -> false.also { Timber.w("Couldn't get the brands token: ${result.error}") }
            }
            delay(if (fetched) REFRESH else RETRY)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private companion object {
        val REFRESH = 30.minutes
        val RETRY = 1.minutes
        const val STOP_TIMEOUT = 5_000L
    }
}
