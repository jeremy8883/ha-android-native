package io.homeassistant.companion.android.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.NotificationsRepository
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.notification.PersistentNotification
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** The server's persistent notifications, for the sidebar's count, the menu button's dot and the drawer. */
@HiltViewModel
internal class NotificationsViewModel @Inject constructor(private val repository: NotificationsRepository) :
    ViewModel() {
    private val retries = MutableStateFlow(0)
    private val _failures = Channel<LoadError>(Channel.BUFFERED)

    /** The notifications, newest first: loading, loaded, or why they couldn't be. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<Loadable<List<PersistentNotification>>> =
        retries.flatMapLatest { repository.notifications() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Whether there are notifications, for the menu button's dot. */
    val hasNotifications: StateFlow<Boolean> = notifications.map { it.valueOrNull?.isNotEmpty() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    private val _drawerOpen = MutableStateFlow(false)

    /** Whether the notification drawer is shown. */
    val drawerOpen: StateFlow<Boolean> = _drawerOpen

    /** Show the notification drawer. */
    fun openDrawer() {
        _drawerOpen.value = true
    }

    /** Hide the notification drawer. */
    fun closeDrawer() {
        _drawerOpen.value = false
    }

    /** Why dismissing failed, once each. */
    val failures: Flow<LoadError> = _failures.receiveAsFlow()

    /** Subscribe again after a failure. */
    fun retry() {
        retries.value++
    }

    /** Dismiss the notification [id]. */
    fun dismiss(id: String) = report { repository.dismiss(id) }

    /** Dismiss every notification. */
    fun dismissAll() = report { repository.dismissAll() }

    private fun report(call: suspend () -> LoadError?) {
        viewModelScope.launch {
            call()?.let { error ->
                Timber.w("Couldn't dismiss notifications: $error")
                _failures.send(error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
