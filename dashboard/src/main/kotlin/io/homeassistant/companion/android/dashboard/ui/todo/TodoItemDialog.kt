package io.homeassistant.companion.android.dashboard.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.todo.TodoFeatures
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import io.homeassistant.companion.android.dashboard.todo.TodoListModel
import io.homeassistant.companion.android.dashboard.todo.TodoStatus
import io.homeassistant.companion.android.dashboard.todo.completedAt
import io.homeassistant.companion.android.dashboard.todo.createTodoItem
import io.homeassistant.companion.android.dashboard.todo.deleteTodoItems
import io.homeassistant.companion.android.dashboard.todo.dueParts
import io.homeassistant.companion.android.dashboard.todo.updateTodoItem
import io.homeassistant.companion.android.dashboard.ui.moreinfo.ConfirmDialog
import io.homeassistant.companion.android.dashboard.ui.moreinfo.DateEditor
import io.homeassistant.companion.android.dashboard.ui.moreinfo.TimeEditor
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Port of `dialog-todo-item-editor`: an item's box (once it exists), summary, completion time, description and due
 * date (and time, for lists that keep one), with add, or save and delete (after confirming).
 */
@Composable
internal fun TodoItemDialog(
    edit: TodoEdit,
    model: TodoListModel,
    hass: HassSnapshot,
    today: LocalDate,
    onAction: (CardAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val item = (edit as? TodoEdit.Change)?.item
    val draft = remember(edit) { TodoDraft(edit, hass.formats.zone) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editable = item == null || model.features.update
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(hass.localize("$STRINGS.${if (item == null) "add" else "edit"}"))
                Text(
                    hass.states[model.entityId]?.stateName() ?: model.entityId,
                    style = HATextStyle.BodyMedium,
                    color = LocalHAColorScheme.current.colorTextSecondary,
                )
            }
        },
        text = { DraftFields(draft, item, model.features, editable, hass, today) },
        confirmButton = {
            HAPlainButton(
                hass.localize(if (item == null) "$STRINGS.add" else "$STRINGS.save"),
                {
                    onAction(draft.action(model, item))
                    onDismiss()
                },
                enabled = draft.summary.isNotEmpty() && editable,
            )
        },
        dismissButton = {
            if (item != null && model.features.delete) {
                HAPlainButton(hass.localize("$STRINGS.delete"), {
                    confirmDelete = true
                }, variant = ButtonVariant.DANGER)
            }
        },
    )
    if (confirmDelete && item != null) {
        ConfirmDialog(
            title = hass.localize("$STRINGS.confirm_delete.delete"),
            text = hass.localize("$STRINGS.confirm_delete.prompt"),
            confirm = hass.localize("ui.common.delete"),
            onConfirm = {
                confirmDelete = false
                onAction(deleteTodoItems(model.entityId, listOf(item.uid)))
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The item being edited: its fields as typed, and the call saving it. */
private class TodoDraft(edit: TodoEdit, private val zone: ZoneId) {
    private val item = (edit as? TodoEdit.Change)?.item
    private val due = item?.dueParts(zone)
    var summary by mutableStateOf(item?.summary ?: (edit as TodoEdit.Create).summary)
    var checked by mutableStateOf(item?.status == TodoStatus.Completed)
    var description by mutableStateOf(item?.description.orEmpty())
    var dueDate by mutableStateOf(due?.first)
    var dueTime by mutableStateOf(due?.second)

    /** The due date, or date-time (in UTC, as `toISOString`), as the calls take it. */
    private fun dueValue(): String? = dueDate?.let { date ->
        dueTime?.let { time -> date.atTime(time).atZone(zone).toInstant().toString() } ?: date.toString()
    }

    /** Add the item, or save [existing] with the fields (clearing what the list can hold and was removed). */
    fun action(model: TodoListModel, existing: TodoItem?): CardAction = if (existing == null) {
        createTodoItem(model.entityId, summary, description, dueValue())
    } else {
        updateTodoItem(
            model.entityId,
            existing.copy(
                summary = summary,
                description = description.ifEmpty { null },
                due = dueValue(),
                status = if (checked) TodoStatus.Completed else TodoStatus.NeedsAction,
            ),
            clearDescription = model.features.description,
            clearDue = model.features.dueDate || model.features.dueDateTime,
        )
    }
}

@Composable
private fun DraftFields(
    draft: TodoDraft,
    item: TodoItem?,
    features: TodoFeatures,
    editable: Boolean,
    hass: HassSnapshot,
    today: LocalDate,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HACheckbox(
                checked = draft.checked,
                onCheckedChange = { draft.checked = it },
                enabled =
                item != null && features.update,
            )
            HATextField(
                value = draft.summary,
                onValueChange = { draft.summary = it },
                label = { Text(hass.localize("$STRINGS.summary")) },
                enabled = editable,
                isError = draft.summary.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item?.completedAt(hass.formats.zone)?.let { at ->
            Text(
                hass.localize("$STRINGS.completed_time", mapOf("datetime" to hass.formats.dateTime(at))),
                style = HATextStyle.BodyMedium.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Start),
            )
        }
        if (features.description) {
            HATextField(
                value = draft.description,
                onValueChange = { draft.description = it },
                label = { Text(hass.localize("$STRINGS.description")) },
                minLines = 2,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (features.dueDate || features.dueDateTime) DueFields(draft, features, editable, hass, today)
    }
}

/** The due date, the time for lists that keep one, and clearing them. */
@Composable
private fun DueFields(
    draft: TodoDraft,
    features: TodoFeatures,
    editable: Boolean,
    hass: HassSnapshot,
    today: LocalDate,
) {
    Text("${hass.localize("$STRINGS.due")}:", style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start))
    Row(verticalAlignment = Alignment.CenterVertically) {
        DateEditor(draft.dueDate?.toString(), enabled = editable) { draft.dueDate = LocalDate.parse(it) }
        if (features.dueDateTime) {
            TimeEditor(draft.dueTime?.toString(), enabled = editable) {
                draft.dueTime = LocalTime.parse(it)
                if (draft.dueDate == null) draft.dueDate = today
            }
        }
        if (draft.dueDate != null) {
            HAPlainButton(hass.localize("ui.common.clear"), {
                draft.dueDate = null
                draft.dueTime = null
            })
        }
    }
}

private const val STRINGS = "ui.components.todo.item"
