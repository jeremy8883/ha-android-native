package io.homeassistant.companion.android.dashboard.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

/** How a kept value is written to the cache and read back; `null` when what was read can't be used. */
interface CacheCodec<T> {
    fun encode(value: T): String

    fun decode(json: String): T?
}

/** A value with the raw responses it was parsed from, which are what gets cached. */
data class Parsed<out P>(val raw: JsonObject, val value: P)

/** Caches [Parsed] values as their raw responses, reading them back with [parse]. */
class ParsedCodec<P>(private val parse: (JsonObject) -> Fetched<P>) : CacheCodec<Parsed<P>> {
    override fun encode(value: Parsed<P>): String = value.raw.toString()

    override fun decode(json: String): Parsed<P>? {
        val raw = decodeObject(json) ?: return null
        return when (val parsed = parse(raw)) {
            is Fetched.Success -> Parsed(raw, parsed.value)
            is Fetched.Failure -> null.also {
                Timber.w("Ignoring a cached response that can't be read: ${parsed.error}")
            }
        }
    }
}

/** Caches a list of JSON objects (such as config flows) as a JSON array. */
object ObjectListCodec : CacheCodec<List<JsonObject>> {
    override fun encode(value: List<JsonObject>): String = JsonArray(value).toString()

    override fun decode(json: String): List<JsonObject>? = try {
        (Json.parseToJsonElement(json) as? JsonArray)?.filterIsInstance<JsonObject>()
    } catch (e: SerializationException) {
        Timber.w(e, "Ignoring a cached list that can't be parsed")
        null
    }
}

/** [json] as a JSON object, or `null` when it isn't one. */
internal fun decodeObject(json: String): JsonObject? = try {
    Json.parseToJsonElement(json).jsonObject
} catch (e: SerializationException) {
    Timber.w(e, "Ignoring cached JSON that can't be parsed")
    null
} catch (e: IllegalArgumentException) {
    Timber.w(e, "Ignoring cached JSON that isn't an object")
    null
}
