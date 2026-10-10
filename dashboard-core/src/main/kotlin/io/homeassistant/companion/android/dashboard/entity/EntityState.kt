package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.model.has
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Authoritative state of one entity, as received from the server.
 *
 * Attributes stay raw JSON so that every derivation sees exactly what the server sent.
 * Timestamps are epoch seconds, as in the compressed `subscribe_entities` format.
 */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject,
    val contextId: String?,
    val lastChanged: Double,
    val lastUpdated: Double,
) {
    val domain: String get() = entityId.substringBefore('.')
}

/** All entity states, keyed by entity id. */
typealias EntityStates = Map<String, EntityState>

/**
 * Apply one `subscribe_entities` event (`a` additions, `r` removals, `c` diffs) to [states].
 * Pure: returns a new map and leaves [states] untouched.
 *
 * Port of `processEvent` in home-assistant-js-websocket@9.6.0 lib/entities.ts. As upstream, a diff
 * for an unknown entity is ignored.
 */
fun applyEntityEvent(states: EntityStates, event: JsonObject): EntityStates {
    val result = states.toMutableMap()

    event.obj("a")?.forEach { (entityId, raw) ->
        val compressed = raw as? JsonObject ?: return@forEach
        val lastChanged = compressed.number("lc") ?: 0.0
        result[entityId] = EntityState(
            entityId = entityId,
            state = compressed.string("s").orEmpty(),
            attributes = compressed.obj("a") ?: EMPTY,
            contextId = contextId(compressed["c"]),
            lastChanged = lastChanged,
            // The server omits `lu` when it equals `lc`
            lastUpdated = compressed.number("lu") ?: lastChanged,
        )
    }

    (event["r"] as? JsonArray)?.forEach { it.stringOrNull?.let(result::remove) }

    event.obj("c")?.forEach { (entityId, raw) ->
        val current = result[entityId] ?: return@forEach
        val diff = raw as? JsonObject ?: return@forEach
        result[entityId] = applyDiff(current, add = diff.obj("+"), remove = diff.obj("-"))
    }

    return result
}

/**
 * This state in the compressed `subscribe_entities` form, which [applyEntityEvent] reads back from an `a` event, for
 * keeping states as the server sent them.
 */
fun EntityState.toCompressed(): JsonObject = buildJsonObject {
    put("s", state)
    put("a", attributes)
    contextId?.let { put("c", it) }
    put("lc", lastChanged)
    // As the server, `lu` only when it differs from `lc`
    if (lastUpdated != lastChanged) put("lu", lastUpdated)
}

/** Decode a `get_states` result (full state objects with ISO timestamps). */
fun parseStates(result: JsonArray): EntityStates = result.filterIsInstance<JsonObject>().mapNotNull {
    val entityId = it.string("entity_id") ?: return@mapNotNull null
    val lastChanged = it.string("last_changed")?.let(::epochSeconds) ?: 0.0
    entityId to EntityState(
        entityId = entityId,
        state = it.string("state").orEmpty(),
        attributes = it.obj("attributes") ?: EMPTY,
        contextId = contextId(it["context"]),
        lastChanged = lastChanged,
        lastUpdated = it.string("last_updated")?.let(::epochSeconds) ?: lastChanged,
    )
}.toMap()

// Android's java.time rejects Instant.parse("…+00:00"), the form get_states uses
private fun epochSeconds(iso: String): Double? = parseJsDate(iso, ZoneOffset.UTC)?.let {
    it.epochSecond + it.nano / NANOS_PER_SECOND
}

private const val NANOS_PER_SECOND = 1_000_000_000.0

private fun applyDiff(current: EntityState, add: JsonObject?, remove: JsonObject?): EntityState {
    var attributes = current.attributes
    add?.obj("a")?.let { attributes = JsonObject(attributes + it) }
    (remove?.get("a") as? JsonArray)?.let { keys ->
        val removed = keys.mapNotNull { it.stringOrNull }.toSet()
        attributes = JsonObject(attributes.filterKeys { it !in removed })
    }

    // A changed `lc` also sets `lu`, which the server then omits
    val lastChanged = add?.number("lc")
    return current.copy(
        state = add?.string("s") ?: current.state,
        attributes = attributes,
        contextId = if (add?.has("c") == true) contextId(add["c"]) ?: current.contextId else current.contextId,
        lastChanged = lastChanged ?: current.lastChanged,
        lastUpdated = lastChanged ?: add?.number("lu") ?: current.lastUpdated,
    )
}

/** The context is sent as a bare id string when it has no parent or user, otherwise as an object. */
private fun contextId(context: JsonElement?): String? = context?.stringOrNull ?: (context as? JsonObject)?.string("id")

private val EMPTY = JsonObject(emptyMap())
