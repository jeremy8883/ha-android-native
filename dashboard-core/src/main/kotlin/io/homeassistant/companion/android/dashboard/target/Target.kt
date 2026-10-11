package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// What a target picker picks: entities, devices, areas, floors and labels. Port of `HassServiceTarget` and of how
// `ha-target-picker` (frontend@20260624.6 src/components/ha-target-picker.ts) adds and removes its items.

/** A kind of target, [key] naming it in a target (`entity_id`, `device_id`...) and in the picker's ids. */
enum class TargetType(val key: String) {
    ENTITY("entity"),
    DEVICE("device"),
    AREA("area"),
    FLOOR("floor"),
    LABEL("label"),
    ;

    /** The target's field, `entity_id`, `device_id`... */
    val field: String get() = "${key}_id"
}

/** One item of a target: its [type] and [id]. */
data class TargetRef(val type: TargetType, val id: String)

/** Port of `HassServiceTarget`: the picked items of each type, in the order they were picked. */
data class Target(val ids: Map<TargetType, List<String>> = emptyMap()) {

    /** The picked items of [type]. */
    operator fun get(type: TargetType): List<String> = ids[type].orEmpty()

    /** Whether nothing is picked. */
    val isEmpty: Boolean get() = ids.values.all { it.isEmpty() }

    /** Port of `_addTargetToValue`: with [ref] added at the end, unless already there or an invalid entity id. */
    operator fun plus(ref: TargetRef): Target = when {
        ref.type == TargetType.ENTITY && !VALID_ENTITY_ID.matches(ref.id) -> this
        ref.id in this[ref.type] -> this
        else -> copy(ids = ids + (ref.type to this[ref.type] + ref.id))
    }

    /** Port of `_addItems`: with [added] of [type] appended. */
    fun plus(type: TargetType, added: List<String>): Target =
        if (added.isEmpty()) this else copy(ids = ids + (type to this[type] + added))

    /** Port of `_removeItem`: without [ref]. */
    operator fun minus(ref: TargetRef): Target {
        val left = this[ref.type] - ref.id
        return copy(ids = if (left.isEmpty()) ids - ref.type else ids + (ref.type to left))
    }

    /**
     * The target as the frontend stores and sends it: each type's ids under its field, one id as a string and more
     * as a list, as `_addTargetToValue` builds it.
     */
    fun toJson(): JsonObject = JsonObject(
        TargetType.entries.mapNotNull { type ->
            val list = this[type]
            when (list.size) {
                0 -> null
                1 -> type.field to JsonPrimitive(list.single())
                else -> type.field to JsonArray(list.map(::JsonPrimitive))
            }
        }.toMap(),
    )

    companion object {
        /** A stored or sent target: each field's string or list of strings; others are left out. */
        fun fromJson(json: JsonObject): Target = Target(
            TargetType.entries.mapNotNull { type ->
                ids(json[type.field]).takeIf { it.isNotEmpty() }?.let { type to it }
            }.toMap(),
        )

        /**
         * Port of `historyLogbookTargetFromQueryParams`: the target of an address's [query] (each field a
         * comma-separated list), `null` when it has none.
         */
        fun fromQuery(query: String?): Target? {
            val params = query.orEmpty().split('&').filter { '=' in it }.associate { pair ->
                pair.substringBefore('=') to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
            }
            val ids = TargetType.entries.mapNotNull { type ->
                params[type.field]?.split(',')?.filter {
                    it.isNotEmpty()
                }?.takeIf { it.isNotEmpty() }?.let { type to it }
            }.toMap()
            return Target(ids).takeIf { !it.isEmpty }
        }

        private fun ids(element: JsonElement?): List<String> = when (element) {
            is JsonArray -> element.mapNotNull { it.stringOrNull }
            is JsonPrimitive -> listOfNotNull(element.stringOrNull)
            else -> emptyList()
        }
    }
}

/** Port of `isValidEntityId`. */
private val VALID_ENTITY_ID = Regex("^(\\w+)\\.(\\w+)$")
