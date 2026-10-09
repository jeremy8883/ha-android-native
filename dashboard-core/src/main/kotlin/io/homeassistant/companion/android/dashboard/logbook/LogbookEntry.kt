package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The logbook's entries as its stream sends them, and how they gather. Ports of `LogbookEntry`
// (frontend@20260624.6 src/data/logbook.ts) and of `ha-logbook._processStreamMessage`
// (src/panels/logbook/ha-logbook.ts).

/**
 * Something the logbook records: an entity's state change, an automation or script run, or an integration's event,
 * with the context it happened in (who or what caused it).
 *
 * @property whenSeconds when it happened, in epoch seconds
 * @property eventType an event entity's event type, its one attribute the logbook sends
 */
data class LogbookEntry(
    val whenSeconds: Double,
    val name: String? = null,
    val message: String? = null,
    val entityId: String? = null,
    val icon: String? = null,
    val source: String? = null,
    val domain: String? = null,
    val state: String? = null,
    val eventType: String? = null,
    val context: LogbookContext = LogbookContext(),
)

/** The context an entry happened in: who started it, and which event (automation, action call, state) caused it. */
data class LogbookContext(
    val id: String? = null,
    val userId: String? = null,
    val eventType: String? = null,
    val domain: String? = null,
    val service: String? = null,
    val entityId: String? = null,
    val entityIdName: String? = null,
    val name: String? = null,
    val state: String? = null,
    val source: String? = null,
    val message: String? = null,
)

/** The entries of a `logbook/event_stream` message, oldest first as sent; `null` when it has none. */
fun parseLogbookEvents(message: JsonObject): List<LogbookEntry>? =
    (message["events"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::parseLogbookEntry) }

private fun parseLogbookEntry(json: JsonObject): LogbookEntry? {
    val whenSeconds = json.number("when") ?: return null
    return LogbookEntry(
        whenSeconds = whenSeconds,
        name = json.string("name"),
        message = json.string("message"),
        entityId = json.string("entity_id"),
        icon = json.string("icon"),
        source = json.string("source"),
        domain = json.string("domain"),
        state = json.string("state"),
        eventType = json.obj("attributes")?.string("event_type"),
        context = LogbookContext(
            id = json.string("context_id"),
            userId = json.string("context_user_id"),
            eventType = json.string("context_event_type"),
            domain = json.string("context_domain"),
            service = json.string("context_service"),
            entityId = json.string("context_entity_id"),
            entityIdName = json.string("context_entity_id_name"),
            name = json.string("context_name"),
            state = json.string("context_state"),
            source = json.string("context_source"),
            message = json.string("context_message"),
        ),
    )
}

/**
 * The entries, newest first, with a stream message's [events] (oldest first) added: on top when they are newer,
 * below when older, sorted in otherwise. Entries from before [purgeBeforeSeconds] are dropped as it goes, as the
 * window moves on. An empty message (which says no more history is coming) changes nothing.
 */
fun List<LogbookEntry>?.withStreamEvents(events: List<LogbookEntry>, purgeBeforeSeconds: Double?): List<LogbookEntry> {
    val newEntries = events.asReversed().toList()
    val kept = when {
        isNullOrEmpty() -> emptyList()
        purgeBeforeSeconds == null || newEntries.isEmpty() -> this
        else -> filter { it.whenSeconds > purgeBeforeSeconds }
    }
    return when {
        newEntries.isEmpty() -> kept
        kept.isEmpty() -> newEntries
        newEntries.last().whenSeconds > kept.first().whenSeconds -> newEntries + kept
        kept.last().whenSeconds > newEntries.first().whenSeconds -> kept + newEntries
        else -> (kept + newEntries).sortedByDescending { it.whenSeconds }
    }
}
