package io.homeassistant.companion.android.dashboard.todo

import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of the to-do data (frontend@20260624.6 src/data/todo.ts): lists, items, and the calls that change them.

/** A to-do item: its [status] `null` when the list doesn't track one, [due] a date ("2026-10-11") or date-time. */
data class TodoItem(
    val uid: String,
    val summary: String,
    val status: TodoStatus?,
    val description: String?,
    val due: String?,
    val completed: String?,
)

/** An item's status. */
enum class TodoStatus(val value: String) {
    NeedsAction("needs_action"),
    Completed("completed"),
}

/** A to-do list: its entity and name. */
data class TodoList(val entityId: String, val name: String)

/** The items of a `todo/item/subscribe` message, `null` when it isn't one. */
fun parseTodoItems(message: JsonObject): List<TodoItem>? = (message["items"] as? JsonArray)?.mapNotNull { element ->
    val item = element as? JsonObject ?: return@mapNotNull null
    val uid = item.string("uid") ?: return@mapNotNull null
    TodoItem(
        uid = uid,
        summary = item.string("summary").orEmpty(),
        status = TodoStatus.entries.firstOrNull { it.value == item.string("status") },
        description = item.string("description")?.ifEmpty { null },
        due = item.string("due")?.ifEmpty { null },
        completed = item.string("completed")?.ifEmpty { null },
    )
}

/** The `todo/item/subscribe` command of [entityId]'s list. */
fun subscribeTodoItemsCommand(entityId: String): WsCommand =
    WsCommand("todo/item/subscribe", JsonObject(mapOf("entity_id" to JsonPrimitive(entityId))))

/** Port of `getTodoLists`: the available lists not hidden, by name. */
fun HassSnapshot.todoLists(): List<TodoList> = states.values
    .filter { it.domain == TODO && it.state != STATE_UNAVAILABLE && registries.entities[it.entityId]?.hidden != true }
    .map { TodoList(it.entityId, it.stateName()) }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/** A list's `TodoListEntityFeature`s. */
data class TodoFeatures(val state: EntityState?) {
    val create: Boolean get() = has(CREATE)
    val delete: Boolean get() = has(DELETE)
    val update: Boolean get() = has(UPDATE)
    val move: Boolean get() = has(MOVE)
    val dueDate: Boolean get() = has(DUE_DATE)
    val dueDateTime: Boolean get() = has(DUE_DATETIME)
    val description: Boolean get() = has(DESCRIPTION)

    private fun has(feature: Int) = state?.supportsFeature(feature) == true

    private companion object {
        const val CREATE = 1
        const val DELETE = 2
        const val UPDATE = 4
        const val MOVE = 8
        const val DUE_DATE = 16
        const val DUE_DATETIME = 32
        const val DESCRIPTION = 64
    }
}

/**
 * Port of the card's `_getDueDate`: a date-time as it is, a date at the end of its day in [zone]; `null` without one
 * (or when it can't be read).
 */
fun TodoItem.dueInstant(zone: ZoneId): Instant? {
    val due = due ?: return null
    return if ('T' in due) {
        parseJsDate(due, zone)
    } else {
        runCatching { LocalDate.parse(due) }.getOrNull()?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)
    }
}

/** The item's due date, and its time when it has one, in [zone] (as the item dialog edits them). */
fun TodoItem.dueParts(zone: ZoneId): Pair<LocalDate, java.time.LocalTime?>? {
    val due = due ?: return null
    return if ('T' in due) {
        parseJsDate(due, zone)?.atZone(zone)?.let { it.toLocalDate() to it.toLocalTime().withNano(0) }
    } else {
        runCatching { LocalDate.parse(due) }.getOrNull()?.let { it to null }
    }
}

/** When the item was completed, `null` when not known. */
fun TodoItem.completedAt(zone: ZoneId): Instant? = completed?.let { parseJsDate(it, zone) }

private const val TODO = "todo"
