package io.homeassistant.companion.android.dashboard.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import io.homeassistant.companion.android.dashboard.todo.TodoListModel
import io.homeassistant.companion.android.dashboard.todo.TodoRow
import io.homeassistant.companion.android.dashboard.todo.TodoStatus
import io.homeassistant.companion.android.dashboard.todo.createTodoItem
import io.homeassistant.companion.android.dashboard.todo.deleteTodoItems
import io.homeassistant.companion.android.dashboard.todo.todoListModel
import io.homeassistant.companion.android.dashboard.todo.toggleTodoItem
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.MarkdownText
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.moreinfo.ConfirmDialog
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Port of `hui-todo-list-card`: the add field, then the items to do, those without a status and the completed ones,
 * each section under its header; a tap on an item opens it. The items follow the list live.
 *
 * Upstream: `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
internal fun TodoListCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    addRequests: Int = 0,
) {
    val entityId = card.json.string("entity") ?: return
    val viewModel = hiltViewModel<TodoListViewModel>(key = "todo-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val model by remember(card) {
        derivedStateOf {
            hass.value?.todoListModel(card, (items as? Loadable.Ready)?.value, now.value?.toInstant() ?: Instant.EPOCH)
        }
    }
    val shown = model
    val localize = hass.value?.localize
    if (shown == null || localize == null) return
    var editing by remember(entityId) { mutableStateOf<TodoEdit?>(null) }
    // The panel's "Add item" button opens a new item in the dialog
    LaunchedEffect(addRequests) { if (addRequests > 0) editing = TodoEdit.Create("") }
    DashboardCardSurface(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE2)) {
            shown.title?.let {
                Text(
                    it,
                    style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    modifier = Modifier.padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
                )
            }
            if (shown.canAdd) AddRow(shown, localize, onAction) { editing = TodoEdit.Create(it) }
            when (val loaded = items) {
                Loadable.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.CenterHorizontally).padding(HADimens.SPACE4),
                )
                is Loadable.Failed -> Text(
                    LocalContext.current.loadErrorText(loaded.error),
                    style = HATextStyle.BodyMedium,
                    color = LocalHAColorScheme.current.colorOnDangerNormal,
                    modifier = Modifier.padding(HADimens.SPACE4),
                )
                is Loadable.Ready -> TodoSections(shown, localize, onAction, viewModel::move) {
                    editing =
                        TodoEdit.Change(it)
                }
            }
        }
    }
    editing?.let { edit ->
        val snapshot = hass.value ?: return@let
        val today = (now.value?.toInstant() ?: Instant.EPOCH).atZone(snapshot.formats.zone).toLocalDate()
        TodoItemDialog(edit, shown, snapshot, today, onAction) { editing = null }
    }
}

/** What the item dialog edits: a new item (its typed [summary]), or an [item] of the list. */
internal sealed interface TodoEdit {
    data class Create(val summary: String) : TodoEdit

    data class Change(val item: TodoItem) : TodoEdit
}

/**
 * The add field ("Add item") with its plus button; with a due date filter, the item opens in the dialog first.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun AddRow(
    model: TodoListModel,
    localize: Localize,
    onAction: (CardAction) -> Unit,
    onDialog: (String) -> Unit,
) {
    var text by remember(model.entityId) { mutableStateOf("") }
    val add = {
        val summary = text
        if (summary.isNotEmpty()) {
            // A list filtered by due date opens the new item in the dialog, for its due date (`_addItem`)
            if (model.addInDialog) onDialog(summary) else onAction(createTodoItem(model.entityId, summary))
            text = ""
        }
    }
    val label = localize("ui.panel.lovelace.cards.todo-list.add_item")
    HATextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(label) },
        enabled = !model.unavailable,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = {
            IconButton(
                onClick = add,
                enabled = !model.unavailable,
                modifier = Modifier.semantics {
                    contentDescription =
                        label
                },
            ) {
                DashboardIcon("mdi:plus", LocalHAColorScheme.current.colorTextPrimary, Modifier.size(HASize.X2L))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
    )
}

/**
 * The sections, or the reorder list while reordering.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun TodoSections(
    model: TodoListModel,
    localize: Localize,
    onAction: (CardAction) -> Unit,
    onMove: (String, String?) -> Unit,
    onOpen: (TodoItem) -> Unit,
) {
    var reordering by remember(model.entityId) { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val strings = "ui.panel.lovelace.cards.todo-list"
    val menu: @Composable () -> Unit = { ReorderMenu(model, reordering, localize) { reordering = !reordering } }
    if (model.unchecked.isEmpty() && model.withoutStatus.isEmpty()) NoItems(localize)
    if (reordering) {
        ReorderList(model, localize, menu, onMove)
        return
    }
    if (model.unchecked.isNotEmpty()) {
        if (model.showHeaders) SectionHeader(localize("$strings.unchecked_items"), menu)
        model.unchecked.forEach { TodoItemRow(it, model, localize, onAction, onOpen) }
    }
    if (model.withoutStatus.isNotEmpty()) {
        if (model.unchecked.isNotEmpty()) HAHorizontalDivider()
        SectionHeader(localize("$strings.no_status_items")) { if (model.unchecked.isEmpty()) menu() }
        model.withoutStatus.forEach { TodoItemRow(it, model, localize, onAction, onOpen) }
    }
    if (model.checked.isNotEmpty()) CompletedSection(model, localize, onAction, onOpen) { confirmClear = true }
    if (confirmClear) {
        ConfirmDialog(
            title = localize("$strings.delete_confirm_title"),
            text = localize("$strings.delete_confirm_text", mapOf("number" to "${model.checked.size}")),
            confirm = localize("ui.common.delete"),
            onConfirm = {
                confirmClear = false
                onAction(deleteTodoItems(model.entityId, model.checked.map { it.item.uid }))
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * The section menu starting or ending reordering, for lists in their own order that can move items.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun ReorderMenu(model: TodoListModel, reordering: Boolean, localize: Localize, onToggle: () -> Unit) {
    if (!model.reorderable) return
    val strings = "ui.panel.lovelace.cards.todo-list"
    SectionMenu(
        localize(if (reordering) "$strings.exit_reorder_items" else "$strings.reorder_items"),
        "mdi:sort",
        onClick = onToggle,
    )
}

/**
 * That there are no items to do.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun NoItems(localize: Localize) {
    Text(
        localize("ui.panel.lovelace.cards.todo-list.no_unchecked_items"),
        style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextPrimary,
        modifier = Modifier.padding(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE4),
    )
}

/**
 * The completed items under their header, whose menu removes them (after confirming).
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun CompletedSection(
    model: TodoListModel,
    localize: Localize,
    onAction: (CardAction) -> Unit,
    onOpen: (TodoItem) -> Unit,
    onClear: () -> Unit,
) {
    val strings = "ui.panel.lovelace.cards.todo-list"
    HAHorizontalDivider()
    if (model.showHeaders) {
        SectionHeader(localize("$strings.checked_items")) {
            if (model.canClear) {
                SectionMenu(
                    localize("$strings.clear_items"),
                    "mdi:delete",
                    danger = true,
                    onClick = onClear,
                )
            }
        }
    }
    model.checked.forEach { TodoItemRow(it, model, localize, onAction, onOpen) }
}

/**
 * The items to do, each with up and down buttons, while reordering.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun ReorderList(
    model: TodoListModel,
    localize: Localize,
    menu: @Composable () -> Unit,
    onMove: (String, String?) -> Unit,
) {
    val movable = model.unchecked + model.withoutStatus
    SectionHeader(localize("ui.panel.lovelace.cards.todo-list.reorder_items"), menu)
    movable.forEachIndexed { index, row ->
        ReorderRow(row, canUp = index > 0, canDown = index < movable.lastIndex, localize) { up ->
            // `todo/item/move` puts the item after the one given (first without one)
            val previous = if (up) movable.getOrNull(index - 2) else movable[index + 1]
            onMove(row.item.uid, previous?.item?.uid)
        }
    }
}

/**
 * A section's header: its title and its menu.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun SectionHeader(title: String, menu: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = HADimens.SPACE4, end = HADimens.SPACE1, top = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
            modifier = Modifier.weight(1f),
        )
        menu()
    }
}

/**
 * A section's ⋮ menu with one item.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun SectionMenu(label: String, icon: String, danger: Boolean = false, onClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    Box {
        IconButton(onClick = {
            open = true
        }) { DashboardIcon("mdi:dots-vertical", colors.colorTextPrimary, Modifier.size(HASize.X2L)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(label, color = if (danger) colors.colorOnDangerNormal else colors.colorTextPrimary) },
                leadingIcon = {
                    DashboardIcon(
                        icon,
                        if (danger) colors.colorOnDangerNormal else colors.colorTextSecondary,
                        Modifier.size(HASize.X2L),
                    )
                },
                onClick = {
                    open = false
                    onClick()
                },
            )
        }
    }
}

/**
 * An item: its box (a dash without a status), its summary (struck through once completed), description and due
 * time (in the danger colour once overdue), and a delete button for lists that can't edit items.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
@Composable
private fun TodoItemRow(
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
        Modifier.fillMaxWidth().clickable(enabled = !model.unavailable) { onOpen(item) }
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
            row.due?.let { due ->
                val color = if (row.overdue && !completed) colors.colorOnDangerNormal else colors.colorTextSecondary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                ) {
                    DashboardIcon("mdi:clock", color, Modifier.size(HASize.M))
                    Text(due, style = HATextStyle.BodyMedium, color = color)
                }
            }
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

/**
 * An item to do while reordering, with its up and down buttons.
 *
 * Upstream: part of `hui-todo-list-card` (src/panels/lovelace/cards/hui-todo-list-card.ts)
 */
// UPSTREAM-DIFF: items move with up and down buttons; upstream drags them by a handle
@Composable
private fun ReorderRow(
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
