package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// Lenient accessors over raw Home Assistant JSON. Configs are user-authored and loosely typed, so a
// value of an unexpected shape reads as absent instead of throwing.

/** The value at [key] when it is a JSON string. */
fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The value at [key] when it is a JSON boolean. */
fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }?.booleanOrNull

/** The value at [key] when it is a JSON number. */
fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }?.doubleOrNull

/** The value at [key] when it is a JSON object. */
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** The value at [key] when it is a JSON array. */
fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** The JSON objects of the array at [key], skipping elements of any other shape. */
fun JsonObject.objects(key: String): List<JsonObject> = array(key)?.filterIsInstance<JsonObject>().orEmpty()

/** Whether [key] is present, matching the TS `"key" in obj` check (a `null` value counts as present). */
fun JsonObject.has(key: String): Boolean = containsKey(key)

/** The element as a string, if it is a JSON string primitive. */
val JsonElement.stringOrNull: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
