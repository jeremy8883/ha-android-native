package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// JavaScript coercion rules for raw JSON values, so ported frontend logic behaves the same on odd inputs.

/** JavaScript `String(value)` for JSON values. */
internal fun JsonElement.jsString(): String = when (this) {
    is JsonPrimitive -> content
    else -> toString()
}

/** JavaScript `Number(value)`: missing is NaN, null and "" are 0, booleans are 0/1, other strings parse or are NaN. */
internal fun jsNumber(value: JsonElement?): Double = when {
    value == null -> Double.NaN
    value is JsonNull -> 0.0
    value !is JsonPrimitive -> Double.NaN
    !value.isString && value.booleanOrNull != null -> if (value.booleanOrNull == true) 1.0 else 0.0
    !value.isString -> value.doubleOrNull ?: Double.NaN
    else -> jsNumber(value.content)
}

/** JavaScript `Number(text)`: blank is 0, decimal and exponent literals parse, anything else is NaN. */
internal fun jsNumber(text: String): Double {
    val trimmed = text.trim()
    return when {
        trimmed.isEmpty() -> 0.0
        JS_DECIMAL.matches(trimmed) -> trimmed.toDouble()
        trimmed == "Infinity" || trimmed == "+Infinity" -> Double.POSITIVE_INFINITY
        trimmed == "-Infinity" -> Double.NEGATIVE_INFINITY
        else -> Double.NaN
    }
}

/** JavaScript truthiness: missing, null, false, 0, NaN and "" are falsy; objects and arrays are truthy. */
internal fun jsTruthy(value: JsonElement?): Boolean = when {
    value == null || value is JsonNull -> false
    value !is JsonPrimitive -> true
    value.isString -> value.content.isNotEmpty()
    value.booleanOrNull != null -> value.booleanOrNull == true
    else -> value.doubleOrNull.let { it != null && it != 0.0 && !it.isNaN() }
}

// Hex and binary literals are not used in states
private val JS_DECIMAL = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")
