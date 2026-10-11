package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.flow.FLOW
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.todo.ConfigFlowDialog
import io.homeassistant.companion.android.dashboard.ui.todo.ListEvent
import io.homeassistant.companion.android.dashboard.ui.todo.TodoListsViewModel

/** Creating and deleting lists on the to-do panel: [onCreate] starts "Create list", [deleter] gives "Delete list". */
internal class TodoListManagement(
    val onCreate: () -> Unit,
    private val viewModel: TodoListsViewModel,
    private val onDelete: (String) -> Unit,
) {
    /**
     * "Delete list" for [entityId] when it's a local list (`local_todo`), else `null`.
     *
     * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
     */
    @Composable
    fun deleter(entityId: String): (() -> Unit)? {
        val entry by remember(entityId) { viewModel.entry(entityId) }.collectAsStateWithLifecycle(Loadable.Loading)
        val local = (entry as? Loadable.Ready)?.value?.string("platform") == LOCAL_TODO
        return if (local) ({ onDelete(entityId) }) else null
    }
}

/**
 * The to-do panel's list management: the "Create list" flow's dialog, choosing the new list once it shows among
 * [lists], "Delete list" after confirming, and what deleting led to. [onChoose] shows a list (`null`: the first).
 *
 * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
internal fun rememberTodoListManagement(
    hass: HassSnapshot,
    lists: List<String>,
    onChoose: (String?) -> Unit,
): TodoListManagement {
    val viewModel = hiltViewModel<TodoListsViewModel>()
    val currentLists by rememberUpdatedState(lists)
    // The lists there were when a list was created: the new one is the one they didn't have
    var before by remember { mutableStateOf<Set<String>?>(null) }
    var outcome by remember { mutableStateOf<ListEvent?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ListEvent.Created -> before = currentLists.toSet()
                is ListEvent.Deleted -> {
                    onChoose(null)
                    if (event.restart) outcome = event
                }
                is ListEvent.DeleteFailed -> outcome = event
            }
        }
    }
    LaunchedEffect(lists, before) {
        val known = before ?: return@LaunchedEffect
        lists.firstOrNull { it !in known }?.let {
            onChoose(it)
            before = null
        }
    }
    val flow by viewModel.flow.collectAsStateWithLifecycle()
    flow?.let { ConfigFlowDialog(it, hass.localize, viewModel::onSubmit, viewModel::onClose) }
    var deleting by remember { mutableStateOf<String?>(null) }
    deleting?.let { entityId ->
        DeleteDialog(hass, entityId, onDelete = { viewModel.onDeleteList(entityId) }, onDone = { deleting = null })
    }
    outcome?.let { event -> OutcomeDialog(hass, event, onClose = { outcome = null }) }
    return remember(viewModel) { TodoListManagement(viewModel::onCreateList, viewModel) { deleting = it } }
}

/**
 * Port of `_deleteList`'s confirmation: "Remove Groceries?".
 *
 * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
private fun DeleteDialog(hass: HassSnapshot, entityId: String, onDelete: () -> Unit, onDone: () -> Unit) {
    val name = hass.states[entityId]?.stateName() ?: entityId
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(hass.localize("$TODO.delete_confirm_title", mapOf("name" to name))) },
        text = { Text(hass.localize("$TODO.delete_confirm_text")) },
        confirmButton = {
            HAAccentButton(
                hass.localize("ui.common.delete"),
                {
                    onDone()
                    onDelete()
                },
                variant = ButtonVariant.DANGER,
            )
        },
        dismissButton = { HAPlainButton(hass.localize("ui.common.cancel"), onDone) },
    )
}

/**
 * That Home Assistant must restart to finish deleting, or why deleting failed.
 *
 * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
private fun OutcomeDialog(hass: HassSnapshot, event: ListEvent, onClose: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val (text, color) = when (event) {
        is ListEvent.Deleted -> hass.localize("$TODO.restart_confirm") to colors.colorTextPrimary
        is ListEvent.DeleteFailed ->
            "${hass.localize("$FLOW.error")}: ${LocalContext.current.loadErrorText(event.error)}" to
                colors.colorOnDangerNormal
        ListEvent.Created -> return
    }
    AlertDialog(
        onDismissRequest = onClose,
        text = { Text(text, color = color) },
        confirmButton = { HAPlainButton(hass.localize("ui.common.ok"), onClose) },
    )
}

private const val LOCAL_TODO = "local_todo"
private const val TODO = "ui.panel.todo"
