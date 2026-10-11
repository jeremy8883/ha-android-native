package io.homeassistant.companion.android.dashboard.model

import java.math.BigDecimal
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// How the frontend turns JSON values into text: JavaScript's `Number.prototype.toString`, `Object.keys` order and
// `JSON.stringify(value, null, indent)`. js-yaml's `dump` is in YamlDump.kt.

/** JavaScript's `String(number)`: integers without a fraction, the shortest digits, exponents from 1e21 and 1e-7. */
fun jsNumberString(value: Double): String = when {
    value.isNaN() -> "NaN"
    value.isInfinite() -> if (value > 0) "Infinity" else "-Infinity"
    value == 0.0 -> "0"
    value < 0 -> "-" + jsNumberString(-value)
    else -> positiveNumberString(value)
}

private fun positiveNumberString(value: Double): String {
    val decimal = BigDecimal(value.toString()).stripTrailingZeros()
    val digits = decimal.unscaledValue().toString()
    val k = digits.length
    // value = 0.digits × 10^n
    val n = k - decimal.scale()
    return when {
        n in k..MAX_PLAIN_EXPONENT -> digits + "0".repeat(n - k)
        n in 1..MAX_PLAIN_EXPONENT -> digits.substring(0, n) + "." + digits.substring(n)
        n in MIN_PLAIN_EXPONENT..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val exponent = n - 1
            val mantissa = if (k == 1) digits else digits[0] + "." + digits.substring(1)
            mantissa + "e" + (if (exponent >= 0) "+" else "-") + abs(exponent)
        }
    }
}

/** The number a JSON number primitive holds, as JavaScript parses it. */
internal fun JsonPrimitive.jsDouble(): Double = content.toDoubleOrNull() ?: Double.NaN

/** JavaScript's `Object.keys` order: integer-like keys ascending first, then the others in insertion order. */
fun JsonObject.jsKeys(): List<String> {
    val (indices, others) = keys.partition { it.isArrayIndex() }
    return indices.sortedBy { it.toLong() } + others
}

private fun String.isArrayIndex(): Boolean {
    val canonical = isNotEmpty() && all(Char::isDigit) && (this == "0" || this[0] != '0')
    return canonical && length <= MAX_INDEX_DIGITS && toLong() < MAX_ARRAY_INDEX
}

/** JavaScript's `JSON.stringify([value], null, [indent])`. */
fun jsonStringify(value: JsonElement, indent: Int = 2): String =
    JsonStringifier(" ".repeat(indent)).apply { append(value, "") }.toString()

/** Port of `JSON.stringify`'s serialisation, indenting each level by [step]. */
private class JsonStringifier(private val step: String) {
    private val out = StringBuilder()

    override fun toString() = out.toString()

    fun append(value: JsonElement, current: String) {
        when (value) {
            is JsonNull -> out.append("null")
            is JsonPrimitive -> appendPrimitive(value)
            is JsonArray -> appendContainer(BRACKETS, value.size, current) { index, inner ->
                append(value[index], inner)
            }
            is JsonObject -> {
                val keys = value.jsKeys()
                appendContainer(BRACES, keys.size, current) { index, inner ->
                    appendString(keys[index])
                    out.append(if (step.isEmpty()) ":" else ": ")
                    append(value.getValue(keys[index]), inner)
                }
            }
        }
    }

    private fun appendPrimitive(value: JsonPrimitive) {
        when {
            value.isString -> appendString(value.content)
            value.booleanOrNull != null -> out.append(value.content)
            else -> out.append(value.jsDouble().takeIf { it.isFinite() }?.let(::jsNumberString) ?: "null")
        }
    }

    private fun appendContainer(delimiters: String, size: Int, current: String, item: (Int, String) -> Unit) {
        if (size == 0) {
            out.append(delimiters)
            return
        }
        val inner = current + step
        out.append(delimiters[0])
        repeat(size) { index ->
            if (index > 0) out.append(',')
            if (step.isNotEmpty()) out.append('\n').append(inner)
            item(index, inner)
        }
        if (step.isNotEmpty()) out.append('\n').append(current)
        out.append(delimiters[1])
    }

    private fun appendString(text: String) {
        out.append('"')
        text.forEachIndexed { index, c ->
            val escape = JSON_ESCAPES[c]
            when {
                escape != null -> out.append(escape)
                c < ' ' || c.isLoneSurrogate(text, index) ->
                    out.append("\\u").append(c.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private fun Char.isLoneSurrogate(text: String, index: Int): Boolean = when {
        isHighSurrogate() -> text.getOrNull(index + 1)?.isLowSurrogate() != true
        isLowSurrogate() -> text.getOrNull(index - 1)?.isHighSurrogate() != true
        else -> false
    }
}

private val JSON_ESCAPES = mapOf(
    '"' to "\\\"",
    '\\' to "\\\\",
    '\b' to "\\b",
    '\u000C' to "\\f",
    '\n' to "\\n",
    '\r' to "\\r",
    '\t' to "\\t",
)

private const val BRACKETS = "[]"
private const val BRACES = "{}"
private const val MAX_PLAIN_EXPONENT = 21
private const val MIN_PLAIN_EXPONENT = -5
private const val HEX = 16
private const val UNICODE_ESCAPE_DIGITS = 4
private const val MAX_INDEX_DIGITS = 10
private const val MAX_ARRAY_INDEX = 4294967295L
