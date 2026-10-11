package io.homeassistant.companion.android.dashboard.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.ConfigFlowRepository
import io.homeassistant.companion.android.dashboard.data.EntityEntryRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.flatMap
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.flow.FlowStep
import io.homeassistant.companion.android.dashboard.model.string
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** The "Create list" flow as it goes: loading, a step with the integration's [translations], or why it failed. */
internal sealed interface ListFlow {
    data object Loading : ListFlow

    /** [step] is shown; [submitting] while its values are sent, [failure] when sending them failed. */
    data class Step(
        val step: FlowStep,
        val translations: Map<String, String>,
        val submitting: Boolean = false,
        val failure: LoadError? = null,
    ) : ListFlow

    data class Failed(val error: LoadError) : ListFlow
}

/** What creating or deleting a list led to. */
internal sealed interface ListEvent {
    /** A list was created; the panel shows it once it appears. */
    data object Created : ListEvent

    /** The list was deleted, and Home Assistant must restart to finish ([restart]). */
    data class Deleted(val restart: Boolean) : ListEvent

    data class DeleteFailed(val error: LoadError) : ListEvent
}

/**
 * Creating and deleting to-do lists, as the to-do panel does (frontend@20260624.6 src/panels/todo/ha-panel-todo.ts):
 * "Create list" runs the `local_todo` config flow, "Delete list" removes a local list's config entry.
 */
@HiltViewModel
internal class TodoListsViewModel @Inject constructor(
    private val flows: ConfigFlowRepository,
    private val entries: EntityEntryRepository,
) : ViewModel() {
    private val _flow = MutableStateFlow<ListFlow?>(null)

    /** The "Create list" flow shown, `null` when none is. */
    val flow: StateFlow<ListFlow?> = _flow.asStateFlow()

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    /** Start creating a list (`_addList`): `local_todo`'s flow with its texts. */
    fun onCreateList() {
        _flow.value = ListFlow.Loading
        viewModelScope.launch {
            val result = flows.translations(LOCAL_TODO, LANGUAGE).flatMap { translations ->
                flows.start(LOCAL_TODO).map { ListFlow.Step(it, translations) }
            }
            _flow.value = when (result) {
                is Fetched.Success -> result.value
                is Fetched.Failure -> ListFlow.Failed(result.error).also {
                    Timber.w("Couldn't start creating a to-do list: ${result.error}")
                }
            }
        }
    }

    /** Send the shown form's [values]; the flow moves on, or shows the form again with its errors. */
    fun onSubmit(values: JsonObject) {
        val shown = _flow.value as? ListFlow.Step ?: return
        _flow.value = shown.copy(submitting = true, failure = null)
        viewModelScope.launch {
            _flow.value = when (val result = flows.submit(shown.step.flowId, values)) {
                is Fetched.Success -> shown.copy(step = result.value, submitting = false)
                is Fetched.Failure -> shown.copy(submitting = false, failure = result.error)
            }
        }
    }

    /** Close the flow: an unfinished one is dropped on the server, as the dialog does; a finished one shows its list. */
    fun onClose() {
        val shown = _flow.value
        _flow.value = null
        val step = (shown as? ListFlow.Step)?.step ?: return
        viewModelScope.launch {
            if (step is FlowStep.CreateEntry) {
                _events.send(ListEvent.Created)
            } else if (step is FlowStep.Form) {
                (flows.cancel(step.flowId) as? Fetched.Failure)?.let { Timber.w("Couldn't drop the flow: ${it.error}") }
            }
        }
    }

    /** [entityId]'s registry entry, which says whether it's a local list that can be deleted. */
    fun entry(entityId: String): Flow<Loadable<JsonObject?>> = entries.entry(entityId)

    /** Delete [entityId]'s list (`_deleteList`): its config entry, when it's a local list. */
    fun onDeleteList(entityId: String) {
        viewModelScope.launch {
            val entry = entries.entry(entityId).first { it !is Loadable.Loading }
            val entryId = (entry as? Loadable.Ready)?.value?.takeIf { it.string("platform") == LOCAL_TODO }
                ?.string("config_entry_id")
            val result = when {
                entry is Loadable.Failed -> Fetched.Failure(entry.error)
                entryId == null -> Fetched.Failure(LoadError.UnexpectedResponse("config/entity_registry/get"))
                else -> flows.deleteEntry(entryId)
            }
            _events.send(
                when (result) {
                    is Fetched.Success -> ListEvent.Deleted(restart = result.value)
                    is Fetched.Failure -> ListEvent.DeleteFailed(result.error).also {
                        Timber.w("Couldn't delete the to-do list $entityId: ${result.error}")
                    }
                },
            )
        }
    }

    private companion object {
        const val LOCAL_TODO = "local_todo"

        /** The language of the bundled frontend strings, which the server's translations are fetched in too. */
        const val LANGUAGE = "en"
    }
}
