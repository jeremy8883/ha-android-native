package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.energy.isoString
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.isNumericEntity
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// What the more-info dialog's logbook section loads. Ports of `computeShowLogBookComponent`
// (frontend@20260624.6 src/dialogs/more-info/const.ts), `ha-more-info-logbook`, and of `subscribeLogbook`,
// `_updateUsers` and `loadTraceContexts` as `ha-logbook` uses them (src/panels/logbook/ha-logbook.ts).

/**
 * Whether [entityId]'s details show its logbook: when the server keeps one, for entities that exist and whose
 * states aren't continuous values (counters, proximity and numeric sensors change too often to list).
 */
fun HassSnapshot.showsLogbook(entityId: String): Boolean {
    val state = states[entityId]
    val domain = entityId.substringBefore('.')
    return LOGBOOK in config.components &&
        state != null &&
        domain !in CONTINUOUS_DOMAINS &&
        !(domain == "sensor" && isNumericEntity(domain, state)) &&
        domain !in NO_HISTORY_DOMAINS
}

/**
 * The subscription to [entityId]'s logbook over the [LOGBOOK_RECENT_SECONDS] before [now], kept up to date: it
 * streams until a year from now, as the frontend's does.
 */
fun logbookStreamCommand(entityId: String, now: Instant): WsCommand = WsCommand(
    "logbook/event_stream",
    buildJsonObject {
        put("start_time", isoString(now.minusSeconds(LOGBOOK_RECENT_SECONDS)))
        put("end_time", isoString(now.plus(STREAM_DAYS, ChronoUnit.DAYS)))
        putJsonArray("entity_ids") { add(JsonPrimitive(entityId)) }
    },
)

/** Entries from before this, in epoch seconds, have left the window that ends at [now]. */
fun logbookPurgeBefore(now: Instant): Double = (now.toEpochMilli() / MILLIS) - LOGBOOK_RECENT_SECONDS

/** The users, which only admins may list; others name the users from their person entities alone. */
val USERS_COMMAND = WsCommand("config/auth/list")

/** The automation and script runs with a trace, which only admins may list. */
val TRACE_CONTEXTS_COMMAND = WsCommand("trace/contexts")

/**
 * Who the users are, to name the user who caused an entry: [names] by user id, and the [systemUserIds] of the
 * users Home Assistant made for itself (Cloud, Cast).
 */
data class LogbookUsers(val names: Map<String, String>, val systemUserIds: Set<String>) {
    companion object {
        val NONE = LogbookUsers(emptyMap(), emptySet())
    }
}

/**
 * The users as `ha-logbook` names them: each person's name for their user, then for admins the [users] list
 * (`config/auth/list`) for the users without a person. [users] is `null` for other users.
 */
fun HassSnapshot.logbookUsers(users: JsonArray?): LogbookUsers {
    val names = mutableMapOf<String, String>()
    states.values.filter { it.domain == "person" }.forEach { person ->
        person.attributes.string("user_id")?.let { userId ->
            person.attributes.string("friendly_name")?.let { names[userId] = it }
        }
    }
    val systemUserIds = mutableSetOf<String>()
    users?.forEach { element ->
        val user = element as? JsonObject ?: return@forEach
        val id = user.string("id") ?: return@forEach
        if (id !in names) user.string("name")?.let { names[id] = it }
        if (user.boolean("system_generated") == true) systemUserIds += id
    }
    return LogbookUsers(names, systemUserIds)
}

/** An automation's or script's run with a trace, which its runs' entries link to. */
data class TraceContext(val domain: String, val itemId: String, val runId: String) {
    /** Where the trace is shown. */
    val path: String get() = "/config/$domain/trace/$itemId?run_id=$runId"
}

/** The `trace/contexts` result: the runs by the context they ran in. */
fun parseTraceContexts(json: JsonObject): Map<String, TraceContext> = json.mapNotNull { (contextId, value) ->
    val run = value as? JsonObject ?: return@mapNotNull null
    val domain = run["domain"]?.stringOrNull ?: return@mapNotNull null
    val itemId = run["item_id"]?.stringOrNull ?: return@mapNotNull null
    val runId = run["run_id"]?.stringOrNull ?: return@mapNotNull null
    contextId to TraceContext(domain, itemId, runId)
}.toMap()

/**
 * The address of [panel] (`history`, `logbook`) for [entityId] from the start of yesterday in [zone], as the more-info
 * sections' "Show more" link.
 */
fun moreInfoPanelPath(panel: String, entityId: String, now: Instant, zone: ZoneId): String {
    val yesterday = LocalDate.ofInstant(now, zone).minusDays(1).atStartOfDay(zone).toInstant()
    val params = listOf("entity_id" to entityId, "start_date" to isoString(yesterday), "back" to "1")
    return "/$panel?" + params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
}

/** How far back the details' logbook goes, in seconds. */
const val LOGBOOK_RECENT_SECONDS = 86_400L

private const val LOGBOOK = "logbook"
private const val STREAM_DAYS = 365L
private const val MILLIS = 1000.0
private val CONTINUOUS_DOMAINS = setOf("counter", "proximity")
private val NO_HISTORY_DOMAINS = setOf("camera", "configurator")
