package io.homeassistant.companion.android.dashboard.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.TodoRepository
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** A to-do list's items while its card shows (`todo/item/subscribe`), and moving them. */
@HiltViewModel
internal class TodoListViewModel @Inject constructor(private val repository: TodoRepository) : ViewModel() {
    private val entityId = MutableStateFlow<String?>(null)

    /** The list's items: loading, loaded, or why they couldn't be. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<Loadable<List<TodoItem>>> = entityId.filterNotNull().flatMapLatest(repository::items)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** Show [id]'s items. */
    fun show(id: String) {
        entityId.value = id
    }

    /** Move [uid] after [previousUid] (first without one); a failure is logged, the list shows the server's order. */
    fun move(uid: String, previousUid: String?) {
        val id = entityId.value ?: return
        viewModelScope.launch {
            repository.move(id, uid, previousUid)?.let { Timber.w("Couldn't move the to-do item: $it") }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
