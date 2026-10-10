package io.homeassistant.companion.android.dashboard.action

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** This action with [from] replaced by [to] in a call's `entity_id` (one or a list), for a stand-in entity. */
fun CardAction.retargeted(from: String, to: String): CardAction {
    if (this !is CardAction.CallService) return this
    val swap = { element: JsonElement ->
        when {
            element is JsonPrimitive && element.content == from -> JsonPrimitive(to)
            element is JsonArray -> JsonArray(
                element.map { if (it is JsonPrimitive && it.content == from) JsonPrimitive(to) else it },
            )
            else -> element
        }
    }
    val data = data?.let { JsonObject(it.mapValues { (key, value) -> if (key == "entity_id") swap(value) else value }) }
    val target = target?.let {
        JsonObject(
            it.mapValues { (key, value) ->
                if (key ==
                    "entity_id"
                ) {
                    swap(value)
                } else {
                    value
                }
            },
        )
    }
    return copy(data = data, target = target)
}
