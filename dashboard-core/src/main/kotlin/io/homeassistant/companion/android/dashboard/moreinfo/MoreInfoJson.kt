package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Reading attributes and writing service data the way the frontend does.

/** The number at [key], `null` when absent or null. */
internal fun JsonObject.numberOrNull(key: String): Double? = this[key]?.takeIf { it !is JsonNull }?.let(::jsNumber)

/** The strings of the list at [key]; none when it isn't a list, as upstream iterates a missing list. */
internal fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { it.stringOrNull }.orEmpty()

/** [value] as JSON, as JavaScript writes it: whole numbers without a fraction, `null` as null. */
internal fun jsonNumber(value: Double?): JsonPrimitive = when {
    value == null -> JsonPrimitive(null as Number?)
    value == Math.floor(value) && !value.isInfinite() -> JsonPrimitive(value.toLong())
    else -> JsonPrimitive(value)
}

/** The decimals a step has, as upstream counts them from its text. */
internal fun fractionDigits(step: Double): Int = jsNumberString(step).substringAfter('.', "").length
