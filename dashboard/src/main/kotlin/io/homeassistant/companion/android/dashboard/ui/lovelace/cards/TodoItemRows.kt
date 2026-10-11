package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import io.homeassistant.companion.android.dashboard.todo.TodoItemTap
import io.homeassistant.companion.android.dashboard.todo.TodoListModel
import io.homeassistant.companion.android.dashboard.todo.TodoRow
import io.homeassistant.companion.android.dashboard.todo.TodoStatus
import io.homeassistant.companion.android.dashboard.todo.deleteTodoItems
import io.homeassistant.companion.android.dashboard.todo.toggleTodoItem
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * An item: its box (a dash without a status), its summary (struck through once completed), description and due
 * time (in the warning colour once overdue), and a delete button for lists that can't edit items. A tap opens it,
 * toggles it or does nothing, as the card's `item_tap_action` says.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
internal fun TodoItemRow(
    row: TodoRow,
    model: TodoListModel,
    localize: Localize,
    onAction: (CardAction) -> Unit,
    onOpen: (TodoItem) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val item = row.item
    val completed = item.status == TodoStatus.Completed
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = !model.unavailable) { onTap(model, item, onAction, onOpen) }
            .padding(end = HADimens.SPACE4, top = HADimens.SPACE1, bottom = HADimens.SPACE1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = when (item.status) {
                TodoStatus.Completed -> ToggleableState.On
                TodoStatus.NeedsAction -> ToggleableState.Off
                null -> ToggleableState.Indeterminate
            },
            onClick = { onAction(toggleTodoItem(model.entityId, item)) },
            enabled = model.canToggle && !model.unavailable,
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.summary,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                ),
                color = if (completed) colors.colorTextSecondary else colors.colorTextPrimary,
            )
            item.description?.let { MarkdownText(it, Modifier.fillMaxWidth()) }
            row.due?.let { DueText(it, overdue = row.overdue && !completed) }
        }
        if (model.showDelete) {
            val label = localize("ui.panel.lovelace.cards.todo-list.delete_item")
            IconButton(
                onClick = {
                    onAction(deleteTodoItems(model.entityId, listOf(item.uid)))
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        label
                },
            ) {
                DashboardIcon("mdi:delete", colors.colorTextSecondary, Modifier.size(HASize.X2L))
            }
        }
    }
}

/** Port of `_itemTap`: open the item, toggle it, or nothing, as the card's `item_tap_action` says. */
private fun onTap(model: TodoListModel, item: TodoItem, onAction: (CardAction) -> Unit, onOpen: (TodoItem) -> Unit) {
    when (model.itemTap) {
        TodoItemTap.Edit -> onOpen(item)
        TodoItemTap.Toggle -> if (model.canToggle) onAction(toggleTodoItem(model.entityId, item))
        TodoItemTap.None -> Unit
    }
}

/**
 * An item's due time after a clock, in the warning colour once [overdue].
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun DueText(due: String, overdue: Boolean) {
    val colors = LocalHAColorScheme.current
    val warning = resolveVariable(WARNING_COLOR, isSystemInDarkTheme()) ?: colors.colorOnWarningNormal
    val color = if (overdue) warning else colors.colorTextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
    ) {
        DashboardIcon("mdi:clock", color, Modifier.size(HASize.M))
        Text(due, style = HATextStyle.BodyMedium, color = color)
    }
}

/**
 * An item to do while reordering, with its up and down buttons.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
// UPSTREAM-DIFF: items move with up and down buttons; upstream drags them by a handle
@Composable
internal fun ReorderRow(
    row: TodoRow,
    canUp: Boolean,
    canDown: Boolean,
    localize: Localize,
    onMove: (up: Boolean) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Row(Modifier.fillMaxWidth().padding(start = HADimens.SPACE4), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.item.summary,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            color = colors.colorTextPrimary,
            modifier = Modifier.weight(1f),
        )
        val label = localize("ui.panel.lovelace.cards.todo-list.drag_and_drop")
        IconButton(
            onClick = { onMove(true) },
            enabled = canUp,
            modifier = Modifier.semantics {
                contentDescription =
                    label
            },
        ) {
            DashboardIcon(
                "mdi:arrow-up",
                if (canUp) colors.colorTextPrimary else colors.colorTextDisabled,
                Modifier.size(HASize.X2L),
            )
        }
        IconButton(
            onClick = { onMove(false) },
            enabled = canDown,
            modifier = Modifier.semantics {
                contentDescription =
                    label
            },
        ) {
            DashboardIcon(
                "mdi:arrow-down",
                if (canDown) colors.colorTextPrimary else colors.colorTextDisabled,
                Modifier.size(HASize.X2L),
            )
        }
    }
}

/** `--warning-color`, an overdue item's due time. */
private const val WARNING_COLOR = "warning-color"
