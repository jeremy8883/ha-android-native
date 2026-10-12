package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.todo.TodoItem
import io.homeassistant.companion.android.dashboard.todo.TodoListModel
import io.homeassistant.companion.android.dashboard.todo.createTodoItem
import io.homeassistant.companion.android.dashboard.todo.deleteTodoItems
import io.homeassistant.companion.android.dashboard.todo.todoListModel
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.moreinfo.components.ConfirmDialog
import io.homeassistant.companion.android.dashboard.ui.panels.todo.TodoItemDialog
import io.homeassistant.companion.android.dashboard.ui.theme.CardHeaderPadding
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
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
        Column(Modifier.fillMaxWidth().padding(todoListPadding(hasTitle = shown.title != null))) {
            shown.title?.let {
                Text(it, style = Css.Text.CardHeader, modifier = Modifier.padding(CardHeaderPadding))
            }
            if (shown.canAdd) AddRow(shown, localize, onAction) { editing = TodoEdit.Create(it) }
            when (val loaded = items) {
                Loadable.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.CenterHorizontally).padding(Css.Space.S4),
                )
                is Loadable.Failed -> Text(
                    LocalContext.current.loadErrorText(loaded.error),
                    style = Css.Text.Body,
                    color = LocalHAColorScheme.current.colorOnDangerNormal,
                    modifier = Modifier.padding(Css.Space.S4),
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Css.Space.S4, vertical = Css.Space.S2),
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
        style = Css.Text.Body.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextPrimary,
        modifier = Modifier.padding(horizontal = Css.Space.S6, vertical = Css.Space.S4),
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
                    "mdi:delete-sweep",
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
        Modifier.fillMaxWidth().padding(start = Css.Space.S4, end = Css.Space.S1, top = Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = Css.Text.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
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

/** 8dp above and below the list, none above under the card header. */
private fun todoListPadding(hasTitle: Boolean) =
    PaddingValues(top = if (hasTitle) 0.cssPx else Css.Space.S2, bottom = Css.Space.S2)
