package io.homeassistant.companion.android.dashboard.todo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The calls changing a to-do list (frontend@20260624.6 src/data/todo.ts).

/** Port of `createItem`: add [summary] (with its description and due date or date-time) to [entityId]'s list. */
fun createTodoItem(
    entityId: String,
    summary: String,
    description: String? = null,
    due: String? = null,
): CardAction.CallService = todoCall(
    entityId,
    "add_item",
    listOfNotNull(
        "item" to JsonPrimitive(summary),
        description?.ifEmpty { null }?.let { "description" to JsonPrimitive(it) },
    ) + dueFields(due),
)

/**
 * Port of `updateItem`: [item] with its fields; a `null` [TodoItem.description] or [TodoItem.due] is sent only
 * when [clearDescription] or [clearDue] (the list can hold them, and they were removed).
 */
fun updateTodoItem(
    entityId: String,
    item: TodoItem,
    clearDescription: Boolean = false,
    clearDue: Boolean = false,
): CardAction.CallService = todoCall(
    entityId,
    "update_item",
    listOfNotNull(
        "item" to JsonPrimitive(item.uid),
        "rename" to JsonPrimitive(item.summary),
        item.status?.let { "status" to JsonPrimitive(it.value) },
        when {
            item.description != null -> "description" to JsonPrimitive(item.description)
            clearDescription -> "description" to JsonNull
            else -> null
        },
    ) + when {
        item.due != null -> dueFields(item.due)
        // `due_date: null` clears either
        clearDue -> listOf("due_date" to JsonNull)
        else -> emptyList()
    },
)

/** [item] checked or unchecked, as tapping its box does. */
fun toggleTodoItem(entityId: String, item: TodoItem): CardAction.CallService = todoCall(
    entityId,
    "update_item",
    listOf(
        "item" to JsonPrimitive(item.uid),
        "rename" to JsonPrimitive(item.summary),
        "status" to JsonPrimitive(
            if (item.status == TodoStatus.NeedsAction) TodoStatus.Completed.value else TodoStatus.NeedsAction.value,
        ),
    ),
)

/** Port of `deleteItems`. */
fun deleteTodoItems(entityId: String, uids: List<String>): CardAction.CallService =
    todoCall(entityId, "remove_item", listOf("item" to JsonArray(uids.map(::JsonPrimitive))))

/** Port of `moveItem`: [uid] after [previousUid] (first without one), `todo/item/move`. */
fun moveTodoItemCommand(entityId: String, uid: String, previousUid: String?): WsCommand = WsCommand(
    "todo/item/move",
    JsonObject(
        listOfNotNull("entity_id" to entityId, "uid" to uid, previousUid?.let { "previous_uid" to it })
            .associate { (key, value) -> key to JsonPrimitive(value) },
    ),
)

private fun dueFields(due: String?): List<Pair<String, JsonElement>> = when {
    due == null -> emptyList()
    'T' in due -> listOf("due_datetime" to JsonPrimitive(due))
    else -> listOf("due_date" to JsonPrimitive(due))
}

private fun todoCall(entityId: String, service: String, data: List<Pair<String, JsonElement>>) = CardAction.CallService(
    TODO,
    service,
    JsonObject(data.toMap()),
    target = JsonObject(mapOf("entity_id" to JsonPrimitive(entityId))),
)

private const val TODO = "todo"
