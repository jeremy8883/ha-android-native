package io.homeassistant.companion.android.dashboard.todo

import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.JsonObject

// Port of `hui-todo-list-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-todo-list-card.ts): its sections,
// sorting, due date filter and the item rows' texts.

/**
 * A to-do list card: the add field ([canAdd]), the sections (to do, without a status, completed) with their texts,
 * whether the list can be reordered ([reorderable]) and cleared of completed items ([canClear]).
 *
 * @property unavailable whether the list is unavailable or unknown, which disables the card
 * @property addInDialog whether a new item opens in the dialog first (lists filtered by due date)
 */
data class TodoListModel(
    val entityId: String,
    val title: String?,
    val canAdd: Boolean,
    val unchecked: List<TodoRow>,
    val withoutStatus: List<TodoRow>,
    val checked: List<TodoRow>,
    val showHeaders: Boolean,
    val reorderable: Boolean,
    val canClear: Boolean,
    val canToggle: Boolean,
    val showDelete: Boolean,
    val unavailable: Boolean,
    val features: TodoFeatures,
    val addInDialog: Boolean = false,
)

/** An item as its row shows it: its due text ("Today", "In 2 days") and whether it's [overdue]. */
data class TodoRow(val item: TodoItem, val due: String?, val overdue: Boolean)

/** The to-do list card [card] with [items] (`null` while loading) at [now]. */
fun HassSnapshot.todoListModel(card: CardConfig, items: List<TodoItem>?, now: Instant): TodoListModel? {
    val json = card.json
    val state = json.string("entity")?.let(states::get) ?: return null
    val entityId = state.entityId
    val features = TodoFeatures(state)
    val sort = json.string("display_order")?.takeIf { it != NONE }
    val endDate = dueEnd(json.obj("due_date_period")?.obj("calendar"), now, formats.zone, formats.firstWeekday)
    val rows = { statuses: Set<TodoStatus?> ->
        sortItems(
            items.orEmpty().filter { item ->
                item.status in statuses &&
                    (endDate == null || item.dueInstant(formats.zone)?.let { it <= endDate } == true)
            },
            sort,
            formats.zone,
        ).map { todoRow(it, now) }
    }
    return TodoListModel(
        entityId = entityId,
        title = json.string("title"),
        canAdd = json.boolean("hide_create") != true && features.create,
        unchecked = rows(setOf(TodoStatus.NeedsAction)),
        withoutStatus = rows(setOf(null)),
        checked = if (json.boolean("hide_completed") == true) emptyList() else rows(setOf(TodoStatus.Completed)),
        showHeaders = json.boolean("hide_section_headers") != true,
        reorderable = sort == null && features.move,
        canClear = features.delete,
        canToggle = features.update,
        showDelete = features.delete && !features.update,
        unavailable = state.state == UNAVAILABLE || state.state == UNKNOWN,
        features = features,
        addInDialog = json.obj("due_date_period") != null,
    )
}

private fun HassSnapshot.todoRow(item: TodoItem, now: Instant): TodoRow {
    val due = item.dueInstant(formats.zone)
    val dateOnly = item.due != null && 'T' !in item.due
    val today =
        due != null && dateOnly && LocalDate.ofInstant(due, formats.zone) == LocalDate.ofInstant(now, formats.zone)
    return TodoRow(
        item = item,
        due = due?.let {
            if (today) {
                localize("ui.panel.lovelace.cards.todo-list.today")
            } else {
                formats.relativeTime(it, now).replaceFirstChar { c -> c.uppercaseChar() }
            }
        },
        overdue = due != null && due < now,
    )
}

/** Port of `_sortItems`: by summary or due date (missing ones last), else as the list has them. */
private fun sortItems(items: List<TodoItem>, sort: String?, zone: ZoneId): List<TodoItem> = when (sort) {
    ALPHA_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.summary })
    ALPHA_DESC -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.summary })
    DUE_ASC -> items.sortedBy { it.dueInstant(zone) ?: Instant.MAX }
    DUE_DESC -> items.sortedWith(
        compareBy<TodoItem> {
            it.dueInstant(zone) == null
        }.thenByDescending { it.dueInstant(zone) },
    )
    else -> items
}

/** Port of `_addPeriod`: the end of the period ([calendar]'s `period`, offset by `offset`) from [now]. */
private fun dueEnd(calendar: JsonObject?, now: Instant, zone: ZoneId, firstWeekday: Int): Instant? {
    val offset = number(calendar?.get("offset"))?.toLong() ?: 0L
    val today = LocalDate.ofInstant(now, zone)
    val last = when (calendar?.string("period")) {
        "day" -> today.plusDays(offset)
        // The week ends the day before the user's first weekday
        "week" -> today.plusWeeks(offset).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(firstWeekday).minus(1)))
        "month" -> today.plusMonths(offset).with(TemporalAdjusters.lastDayOfMonth())
        "year" -> today.plusYears(offset).with(TemporalAdjusters.lastDayOfYear())
        else -> null
    }
    return last?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minus(1, ChronoUnit.MILLIS)
}

private const val NONE = "none"
private const val ALPHA_ASC = "alpha_asc"
private const val ALPHA_DESC = "alpha_desc"
private const val DUE_ASC = "duedate_asc"
private const val DUE_DESC = "duedate_desc"
private const val UNAVAILABLE = "unavailable"
private const val UNKNOWN = "unknown"
