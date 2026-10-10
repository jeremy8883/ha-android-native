package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.display.RelativeUnit
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.TimelineColor
import io.homeassistant.companion.android.dashboard.history.timelineColor
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The rows of the more-info dialog's logbook, which lists one entity's entries without its name, each a dot on a
// rail ("inline" layout). Ports of `computeLogbookItem`, `computeLogbookValue`, `localizeStateMessage` and
// `createHistoricState` (frontend@20260624.6 src/panels/logbook/logbook-entry-model.ts, src/data/logbook.ts), and
// of how `ha-logbook-entry` and `ha-logbook-renderer` draw them (src/panels/logbook/).

/**
 * A row of the details' logbook.
 *
 * @property whenMillis when the entry happened, epoch ms
 * @property text what happened ("Unlocked", "Triggered", "Pressed"), capitalised
 * @property cause what caused it, shown for state changes only
 * @property traceLink the trace of the run, for an automation's or script's run that has one
 * @property dateHeader the day above the row, on the first of each day: "Today · October 9, 2026"
 * @property firstOfDay whether the rail starts at the row's dot
 * @property lastOfDay whether the rail ends at the row's dot, which also has no divider below
 */
data class LogbookRow(
    val whenMillis: Long,
    val text: String,
    val dot: LogbookDot,
    val cause: LogbookCause?,
    val traceLink: String?,
    val dateHeader: String?,
    val firstOfDay: Boolean,
    val lastOfDay: Boolean,
)

/** The colour of a row's dot. */
sealed interface LogbookDot {
    /** The state's colour on the history timeline. */
    data class Timeline(val color: TimelineColor) : LogbookDot

    /** The state's colour ([DisplayColor.State]). */
    data class State(val color: DisplayColor) : LogbookDot

    /** A theme colour by the row's kind: automation runs, integration events, or none for state changes. */
    data class Theme(val variable: String) : LogbookDot

    /** The entity was unavailable: a hollow dot. */
    data object Unavailable : LogbookDot
}

/** The kind of a logbook entry, port of `classifyLogbookEntry`. */
private enum class Category(val dotVariable: String) {
    ENTITY("secondary-text-color"),
    AUTOMATION("light-blue-color"),
    INTEGRATION("teal-color"),
}

/**
 * The rows of [entries] (newest first), as of [now]: the cause names the [users], and runs link to their
 * [traces] (`null` while they aren't known, so no run links).
 */
fun HassSnapshot.logbookRows(
    entries: List<LogbookEntry>,
    users: LogbookUsers,
    traces: Map<String, TraceContext>?,
    now: Instant,
): List<LogbookRow> {
    val days = entries.map { LocalDate.ofInstant(it.instant(), formats.zone) }
    val today = LocalDate.ofInstant(now, formats.zone)
    return entries.mapIndexed { index, entry ->
        val category = entry.category()
        val firstOfDay = index == 0 || days[index - 1] != days[index]
        LogbookRow(
            whenMillis = entry.instant().toEpochMilli(),
            text = logbookValue(entry).replaceFirstChar { it.uppercaseChar() },
            dot = logbookDot(entry, category),
            cause = if (category == Category.ENTITY) logbookCause(entry, users) else null,
            traceLink = entry.context.id
                ?.takeIf { entry.domain in TRIGGER_DOMAINS }
                ?.let { traces?.get(it)?.path },
            dateHeader = if (firstOfDay) dateHeader(days[index], today, entry.instant()) else null,
            firstOfDay = firstOfDay,
            lastOfDay = index == entries.lastIndex || days[index + 1] != days[index],
        )
    }
}

private fun LogbookEntry.instant(): Instant = Instant.ofEpochMilli((whenSeconds * MILLIS).toLong())

private fun LogbookEntry.category(): Category = when {
    // State changes win even for automations and scripts (turning one off is a change, not a run)
    entityId != null && state != null -> Category.ENTITY
    domain in TRIGGER_DOMAINS -> Category.AUTOMATION
    else -> Category.INTEGRATION
}

/** The day header: "Today · …" and "Yesterday · …" for those days, else the date alone. */
private fun HassSnapshot.dateHeader(day: LocalDate, today: LocalDate, instant: Instant): String {
    val date = formats.date(instant)
    return when (val daysAgo = ChronoUnit.DAYS.between(day, today)) {
        0L, 1L -> formats.relative(-daysAgo, RelativeUnit.DAY).replaceFirstChar { it.uppercaseChar() } + " · " + date
        else -> date
    }
}

/**
 * The dot's colour: the state's timeline colour when the entity was available, else the state's colour for a state
 * change, else the row kind's.
 */
private fun HassSnapshot.logbookDot(entry: LogbookEntry, category: Category): LogbookDot {
    val state = entry.state
    val current = entry.entityId?.let { states[it] }
    return when {
        state == UNAVAILABLE -> LogbookDot.Unavailable
        !state.isNullOrEmpty() -> LogbookDot.Timeline(timelineColor(entry.entityId.orEmpty(), state))
        category == Category.ENTITY && current != null ->
            stateColor(historicState(current, state.orEmpty()))?.let { LogbookDot.State(it) }
        else -> null
    } ?: LogbookDot.Theme(category.dotVariable)
}

/** Port of `computeLogbookValue`: what the row says happened, empty when nothing is known. */
private fun HassSnapshot.logbookValue(entry: LogbookEntry): String {
    val domain = entry.entityId?.substringBefore('.') ?: entry.domain
    val state = entry.state
    return when {
        entry.entityId != null && !state.isNullOrEmpty() ->
            states[entry.entityId]?.let { stateMessage(historicState(it, state), entry) } ?: state
        domain in TRIGGER_DOMAINS && entry.isRun() ->
            localize(if (domain == "script") "$LOGBOOK_STRINGS.script_ran" else "$LOGBOOK_STRINGS.automation_triggered")
        entry.message != null -> withEntityName(
            if (entry.hasContext()) stripEntityId(entry.message, entry.context.entityId) else entry.message,
        )
        else -> ""
    }
}

/** Whether an automation's or script's entry is a run: it says what triggered it, or who. */
private fun LogbookEntry.isRun(): Boolean = !source.isNullOrEmpty() || hasContext() || !context.userId.isNullOrEmpty()

/** Port of `localizeStateMessage`: an event's type, a press's or a run's word, else the state as the UI shows it. */
private fun HassSnapshot.stateMessage(historic: EntityState, entry: LogbookEntry): String {
    val domain = historic.domain
    val action = STATE_ACTION_MESSAGES[domain]
    return when {
        domain == "event" ->
            entry.eventType
                ?.let { formatEntityAttributeValue(historic, "event_type", JsonPrimitive(it)) }
                ?: localize("$MESSAGES.detected_event_no_type")
        action != null -> localize("$MESSAGES.$action")
        else -> formatEntityState(historic, historic.state)
    }
}

/** Port of `createHistoricState`: the entity as it was, with [state] and only its attributes that don't change. */
private fun historicState(current: EntityState, state: String): EntityState = current.copy(
    state = state,
    attributes = JsonObject(current.attributes.filterKeys { it in STATIC_ATTRIBUTES }),
)

private fun stripEntityId(message: String, entityId: String?) =
    if (entityId == null) message else message.replace(entityId, " ")

/** Port of `_formatMessageWithPossibleEntity` as text: the first known entity id in [message] by its name. */
private fun HassSnapshot.withEntityName(message: String): String {
    val words = message.split(" ")
    val index = if ('.' in message) words.indexOfFirst { it in states } else -1
    val entity = words.getOrNull(index)?.let { states[it] } ?: return message
    val name = entity.attributes.string("friendly_name") ?: entity.entityId
    return (words.take(index) + name + words.drop(index + 1)).joinToString(" ")
}

private const val MILLIS = 1000.0
private const val UNAVAILABLE = "unavailable"
private const val MESSAGES = "$LOGBOOK_STRINGS.messages"

/** The attributes `createHistoricState` keeps: those a past state reads the same. */
private val STATIC_ATTRIBUTES = setOf(
    "device_class",
    "unit_of_measurement",
    "state_class",
    "options",
    "source_type",
    "has_date",
    "has_time",
)

/** The word for a timestamp domain's state, in place of its time (`STATE_ACTION_MESSAGES`). */
private val STATE_ACTION_MESSAGES = mapOf(
    "button" to "pressed",
    "input_button" to "pressed",
    "scene" to "activated",
    "tag" to "scanned",
    "image" to "updated",
    "notify" to "sent",
    "wake_word" to "detected",
    "stt" to "transcribed",
    "tts" to "spoke",
    "conversation" to "responded",
    "ai_task" to "ran",
    "infrared" to "command_sent",
    "radio_frequency" to "command_sent",
)
