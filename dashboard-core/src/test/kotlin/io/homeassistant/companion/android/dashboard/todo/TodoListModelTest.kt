package io.homeassistant.companion.android.dashboard.todo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TodoListModelTest {
    private val hass = GoldenFixture("test-instance").hass
    private val now = Instant.parse("2026-10-11T10:00:00Z")
    private val items = parseTodoItems(
        json(
            """{"items": [
                {"uid": "1", "summary": "milk", "status": "needs_action", "due": "2026-10-11"},
                {"uid": "2", "summary": "Bread", "status": "completed"},
                {"uid": "3", "summary": "apples", "status": "needs_action", "due": "2026-10-09T08:00:00+00:00"},
                {"uid": "4", "summary": "Note", "status": null}
            ]}""",
        ),
    )!!

    @Test
    fun `Given items when deriving the card then they sit in their sections, in the list's order`() {
        val model = hass.todoListModel(card(), items, now)!!
        assertEquals(listOf("milk", "apples"), model.unchecked.map { it.item.summary })
        assertEquals(listOf("Note"), model.withoutStatus.map { it.item.summary })
        assertEquals(listOf("Bread"), model.checked.map { it.item.summary })
    }

    @Test
    fun `Given a due date today and one passed when deriving the rows then they read Today and overdue`() {
        val rows = hass.todoListModel(card(), items, now)!!.unchecked
        assertEquals("Today", rows[0].due)
        assertEquals(true, rows[1].overdue)
    }

    @Test
    fun `Given an order when deriving the card then the items are sorted by it`() {
        val alpha = hass.todoListModel(card(""", "display_order": "alpha_asc""""), items, now)!!
        assertEquals(listOf("apples", "milk"), alpha.unchecked.map { it.item.summary })
        val due = hass.todoListModel(card(""", "display_order": "duedate_asc""""), items, now)!!
        assertEquals(listOf("apples", "milk"), due.unchecked.map { it.item.summary })
        assertEquals(false, due.reorderable)
    }

    @Test
    fun `Given item_tap_action when deriving the card then a tap edits, toggles or does nothing as _itemTap`() {
        assertEquals(TodoItemTap.Edit, hass.todoListModel(card(), items, now)!!.itemTap)
        assertEquals(TodoItemTap.Edit, hass.todoListModel(card(""", "item_tap_action": "edit""""), items, now)!!.itemTap)
        assertEquals(TodoItemTap.Toggle, hass.todoListModel(card(""", "item_tap_action": "toggle""""), items, now)!!.itemTap)
        assertEquals(TodoItemTap.None, hass.todoListModel(card(""", "item_tap_action": "none""""), items, now)!!.itemTap)
    }

    @Test
    fun `Given an item when ticking, editing and deleting it then the calls are upstream's`() {
        val milk = items.first()
        val toggle = toggleTodoItem("todo.shopping_list", milk)
        assertEquals(json("""{"item": "1", "rename": "milk", "status": "completed"}"""), toggle.data)
        assertEquals(JsonObject(mapOf("entity_id" to JsonPrimitive("todo.shopping_list"))), toggle.target)
        val edit = updateTodoItem("todo.list", milk.copy(due = null, description = null), clearDescription = true, clearDue = true)
        assertEquals(JsonNull, edit.data!!["description"])
        assertEquals(JsonNull, edit.data!!["due_date"])
        assertEquals("2026-10-12T08:00:00Z", createTodoItem("todo.list", "Eggs", due = "2026-10-12T08:00:00Z").data!!["due_datetime"]?.let { (it as JsonPrimitive).content })
        assertEquals(json("""{"entity_id": "todo.list", "uid": "4", "previous_uid": "1"}"""), moveTodoItemCommand("todo.list", "4", "1").params)
    }

    private fun card(extra: String = "") = CardConfig(json("""{"type": "todo-list", "entity": "todo.shopping_list"$extra}"""))
}
