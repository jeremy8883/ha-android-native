package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Port of js-yaml's `dump` (js-yaml@4 lib/dumper.js, with the default schema's implicit types) with the options of
// `ha-yaml-editor` (frontend@20260624.6 src/components/ha-yaml-editor.ts): double quotes, no references.

/**
 * js-yaml's `dump(value, { quotingType: '"', noRefs: true })` as `ha-yaml-editor` shows a value: block mappings and
 * sequences indented by two, scalars plain when they can't be read as another type, else double-quoted, multi-line
 * strings as literal blocks and long ones folded at 80 columns. Ends with a line break. With [singleQuotes], js-yaml's
 * default quoting instead (`dump(value)`, as `ha-attribute-value` shows an object): single quotes where they can
 * hold the text.
 */
fun yamlDump(value: JsonElement, singleQuotes: Boolean = false): String =
    YamlNodes(singleQuotes).node(value, 0, compact = true) + "\n"

/** Port of `writeNode` and the block writers. */
private class YamlNodes(private val singleQuotes: Boolean) {
    fun node(value: JsonElement, level: Int, compact: Boolean, isKey: Boolean = false): String = when (value) {
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            value.isString -> scalar(value.content, level, isKey)
            value.booleanOrNull != null -> value.content
            else -> number(value.jsDouble())
        }
        is JsonObject -> if (value.isEmpty()) "{}" else blockMapping(value, level, compact)
        is JsonArray -> if (value.isEmpty()) "[]" else blockSequence(value, level, compact)
    }

    private fun blockMapping(value: JsonObject, level: Int, compact: Boolean): String = buildString {
        value.jsKeys().forEach { key ->
            if (!compact || isNotEmpty()) append(nextLine(level))
            append(node(JsonPrimitive(key), level + 1, compact = true, isKey = true))
            val dump = node(value.getValue(key), level + 1, compact = false)
            append(if (dump.startsWith('\n')) ":" else ": ")
            append(dump)
        }
    }

    private fun blockSequence(value: JsonArray, level: Int, compact: Boolean): String = buildString {
        value.forEach { item ->
            if (!compact || isNotEmpty()) append(nextLine(level))
            val dump = node(item, level + 1, compact = true)
            append(if (dump.startsWith('\n')) "-" else "- ")
            append(dump)
        }
    }

    private fun nextLine(level: Int) = "\n" + " ".repeat(YAML_INDENT * level)

    /** The int and float types' `represent`: an integer as is, a float's exponent with the dot YAML needs. */
    private fun number(value: Double): String = when {
        value.isNaN() -> ".nan"
        value == Double.POSITIVE_INFINITY -> ".inf"
        value == Double.NEGATIVE_INFINITY -> "-.inf"
        value == 0.0 && 1 / value < 0 -> "-0.0"
        value % 1 == 0.0 -> jsNumberString(value)
        else -> jsNumberString(value).let {
            if (SCIENTIFIC_WITHOUT_DOT.containsMatchIn(it)) it.replaceFirst("e", ".e") else it
        }
    }

    private val quote = if (singleQuotes) "'" else "\""

    /** Port of `writeScalar`. */
    private fun scalar(text: String, level: Int, isKey: Boolean): String {
        val indent = YAML_INDENT * maxOf(1, level)
        val lineWidth = maxOf(minOf(LINE_WIDTH, MIN_LINE_WIDTH), LINE_WIDTH - indent)
        return when {
            text.isEmpty() -> quote.repeat(2)
            text in DEPRECATED_BOOLEANS || DEPRECATED_BASE60.matches(text) -> quote + text + quote
            else -> when (YamlStyles.choose(text, isKey, lineWidth)) {
                ScalarStyle.PLAIN -> text
                ScalarStyle.LITERAL -> "|" + YamlBlocks.header(text) + YamlBlocks.indent(text, indent)
                ScalarStyle.FOLDED ->
                    ">" + YamlBlocks.header(text) + YamlBlocks.indent(YamlBlocks.fold(text, lineWidth), indent)
                ScalarStyle.DOUBLE -> "\"" + YamlBlocks.escape(text) + "\""
                ScalarStyle.QUOTED -> if (singleQuotes) {
                    "'" + text.replace("'", "''") + "'"
                } else {
                    "\"" + YamlBlocks.escape(text) + "\""
                }
            }
        }
    }
}

/** [DOUBLE] for text only double quotes can hold, [QUOTED] in the dump's quoting type. */
private enum class ScalarStyle { PLAIN, LITERAL, FOLDED, DOUBLE, QUOTED }

/** What `chooseScalarStyle` finds going through a string. */
private data class ScalarScan(
    val plain: Boolean,
    val hasLineBreak: Boolean,
    val hasFoldableLine: Boolean,
    val printable: Boolean,
)

/** Port of `chooseScalarStyle` and the checks it makes, always in block context. */
private object YamlStyles {
    fun choose(text: String, singleLineOnly: Boolean, lineWidth: Int): ScalarStyle {
        val scan = scan(text, singleLineOnly, lineWidth)
        return when {
            !scan.printable -> ScalarStyle.DOUBLE
            !scan.hasLineBreak && !scan.hasFoldableLine ->
                if (scan.plain && !isAmbiguous(text)) ScalarStyle.PLAIN else ScalarStyle.QUOTED
            scan.hasFoldableLine -> ScalarStyle.FOLDED
            else -> ScalarStyle.LITERAL
        }
    }

    private fun scan(text: String, singleLineOnly: Boolean, lineWidth: Int): ScalarScan {
        val codePoints = text.codePoints().toArray()
        var plain = isPlainSafeFirst(codePoints.first()) && isPlainSafeLast(codePoints.last())
        var hasLineBreak = false
        var hasFoldableLine = false
        var previousLineBreak = -1
        var prev: Int? = null
        var index = 0
        for (c in codePoints) {
            if (!singleLineOnly && c == LF) {
                hasLineBreak = true
                hasFoldableLine = hasFoldableLine || foldable(text, index, previousLineBreak, lineWidth)
                previousLineBreak = index
            } else if (!isPrintable(c)) {
                return ScalarScan(plain = false, hasLineBreak = false, hasFoldableLine = false, printable = false)
            }
            plain = plain && isPlainSafe(c, prev)
            prev = c
            index += Character.charCount(c)
        }
        if (!singleLineOnly) hasFoldableLine = hasFoldableLine || foldable(text, index, previousLineBreak, lineWidth)
        return ScalarScan(plain, hasLineBreak, hasFoldableLine, printable = true)
    }

    /** A line ending at [end] is too long and not more indented. */
    private fun foldable(text: String, end: Int, previousLineBreak: Int, lineWidth: Int) =
        end - previousLineBreak - 1 > lineWidth && text.getOrNull(previousLineBreak + 1) != ' '

    /** Whether the default schema reads [text] as another type (`testImplicitResolving`). */
    private fun isAmbiguous(text: String): Boolean {
        val float = YAML_FLOAT.matches(text) && !text.endsWith('_')
        val timestamp = YAML_DATE.matches(text) || YAML_TIMESTAMP.matches(text)
        return text in YAML_NULLS || text in YAML_BOOLEANS || text == MERGE_KEY || isInteger(text) || float || timestamp
    }

    /** Port of `resolveYamlInteger`. */
    private fun isInteger(text: String): Boolean {
        val body = if (text.startsWith('-') || text.startsWith('+')) text.drop(1) else text
        val radix = RADIX_DIGITS.entries.firstOrNull { body.startsWith(it.key) }
        // After a leading zero, decimal digits go on from the next character, which can't be an underscore
        val digits = radix?.let { body.drop(it.key.length) } ?: body.removePrefix("0")
        val valid = radix?.value ?: DECIMAL_DIGITS
        val wellFormed = (radix != null || !digits.startsWith('_')) && !digits.endsWith('_')
        return body == "0" || (wellFormed && digits.any { it != '_' } && digits.all { it == '_' || it in valid })
    }

    /** Port of `isPlainSafe` in block context. */
    private fun isPlainSafe(c: Int, prev: Int?): Boolean {
        val cIsNsCharOrWhitespace = isNsCharOrWhitespace(c)
        val cIsNsChar = cIsNsCharOrWhitespace && !isWhitespace(c)
        val safe = cIsNsCharOrWhitespace && c != SHARP && !(prev == COLON && !cIsNsChar)
        val sharpAfterChar = isNsCharOrWhitespace(prev) && !isWhitespace(prev) && c == SHARP
        return safe || sharpAfterChar || (prev == COLON && cIsNsChar)
    }

    private fun isPlainSafeFirst(c: Int) =
        isPrintable(c) && c != BOM && !isWhitespace(c) && c.toChar() !in PLAIN_UNSAFE_FIRST

    private fun isPlainSafeLast(c: Int) = !isWhitespace(c) && c != COLON

    private fun isNsCharOrWhitespace(c: Int?) = c != null && isPrintable(c) && c != BOM && c != CR && c != LF
}

/** Port of the block scalars' writers and `escapeString`. */
private object YamlBlocks {
    /** Port of `blockHeader`: the indentation indicator, then the chomping one. */
    fun header(text: String): String {
        val indicator = if (LEADING_SPACE.containsMatchIn(text)) YAML_INDENT.toString() else ""
        val clip = text.endsWith('\n')
        val keep = clip && (text.dropLast(1).endsWith('\n') || text == "\n")
        val chomp = when {
            keep -> "+"
            clip -> ""
            else -> "-"
        }
        return indicator + chomp + "\n"
    }

    /** Port of `indentString` (every line but the empty ones) then `dropEndingNewline`. */
    fun indent(text: String, spaces: Int): String {
        val indent = " ".repeat(spaces)
        return LINES.findAll(text).map { it.value }.filter { it.isNotEmpty() }
            .joinToString("") { if (it != "\n") indent + it else it }
            .removeSuffix("\n")
    }

    /** Port of `foldString`. */
    fun fold(text: String, width: Int): String {
        val firstBreak = text.indexOf('\n').let { if (it == -1) text.length else it }
        val result = StringBuilder(foldLine(text.substring(0, firstBreak), width))
        var prevMoreIndented = text[0] == '\n' || text[0] == ' '
        FOLD_LINES.findAll(text, firstBreak).forEach { match ->
            val (prefix, line) = match.destructured
            val moreIndented = line.startsWith(' ')
            result.append(prefix)
            if (!prevMoreIndented && !moreIndented && line.isNotEmpty()) result.append('\n')
            result.append(foldLine(line, width))
            prevMoreIndented = moreIndented
        }
        return result.toString()
    }

    /** Port of `foldLine`: greedy breaks at spaces followed by a non-space. */
    private fun foldLine(line: String, width: Int): String {
        if (line.isEmpty() || line[0] == ' ') return line
        var start = 0
        var curr = 0
        val result = StringBuilder()
        BREAKS.findAll(line).forEach { match ->
            val next = match.range.first
            if (next - start > width) {
                val end = if (curr > start) curr else next
                result.append('\n').append(line, start, end)
                start = end + 1
            }
            curr = next
        }
        result.append('\n')
        if (line.length - start > width && curr > start) {
            result.append(line, start, curr).append('\n').append(line.substring(curr + 1))
        } else {
            result.append(line.substring(start))
        }
        return result.substring(1)
    }

    /** Port of `escapeString`. */
    fun escape(text: String): String = buildString {
        text.codePoints().forEach { c ->
            val sequence = YAML_ESCAPES[c]
            when {
                sequence != null -> append(sequence)
                isPrintable(c) -> appendCodePoint(c)
                else -> append(hexEscape(c))
            }
        }
    }

    /** Port of `encodeHex`. */
    private fun hexEscape(c: Int): String {
        val (handle, length) = when {
            c <= MAX_BYTE -> 'x' to BYTE_DIGITS
            c <= MAX_BMP -> 'u' to BMP_DIGITS
            else -> 'U' to ASTRAL_DIGITS
        }
        return "\\" + handle + c.toString(HEX_RADIX).uppercase().padStart(length, '0')
    }
}

/** Port of `isPrintable`. */
private fun isPrintable(c: Int): Boolean {
    val bmp = c in PRINTABLE_BMP && c != LINE_SEPARATOR && c != PARAGRAPH_SEPARATOR
    return c in PRINTABLE_ASCII || bmp || (c in PRINTABLE_PRIVATE && c != BOM) || c in PRINTABLE_ASTRAL
}

private fun isWhitespace(c: Int?) = c == SPACE || c == TAB

private const val YAML_INDENT = 2
private const val LINE_WIDTH = 80
private const val MIN_LINE_WIDTH = 40
private const val TAB = 0x09
private const val LF = 0x0A
private const val CR = 0x0D
private const val SPACE = 0x20
private const val SHARP = 0x23
private const val COLON = 0x3A
private const val BOM = 0xFEFF
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
private const val MAX_BYTE = 0xFF
private const val MAX_BMP = 0xFFFF
private const val BYTE_DIGITS = 2
private const val BMP_DIGITS = 4
private const val ASTRAL_DIGITS = 8
private const val HEX_RADIX = 16
private const val MERGE_KEY = "<<"
private const val PLAIN_UNSAFE_FIRST = "-?:,[]{}#&*!|=>'\"%@`"
private const val PRINTABLE_ASCII_FIRST = 0x20
private const val PRINTABLE_ASCII_LAST = 0x7E
private const val PRINTABLE_BMP_FIRST = 0xA1
private const val PRINTABLE_BMP_LAST = 0xD7FF
private const val PRINTABLE_PRIVATE_FIRST = 0xE000
private const val PRINTABLE_PRIVATE_LAST = 0xFFFD
private const val PRINTABLE_ASTRAL_FIRST = 0x10000
private const val PRINTABLE_ASTRAL_LAST = 0x10FFFF
private val PRINTABLE_ASCII = PRINTABLE_ASCII_FIRST..PRINTABLE_ASCII_LAST
private val PRINTABLE_BMP = PRINTABLE_BMP_FIRST..PRINTABLE_BMP_LAST
private val PRINTABLE_PRIVATE = PRINTABLE_PRIVATE_FIRST..PRINTABLE_PRIVATE_LAST
private val PRINTABLE_ASTRAL = PRINTABLE_ASTRAL_FIRST..PRINTABLE_ASTRAL_LAST
private val DECIMAL_DIGITS = ('0'..'9').toSet()
private val RADIX_DIGITS = mapOf(
    "0b" to setOf('0', '1'),
    "0x" to (('0'..'9') + ('a'..'f') + ('A'..'F')).toSet(),
    "0o" to ('0'..'7').toSet(),
)
private val LINES = Regex("[^\\n]*\\n?")
private val FOLD_LINES = Regex("(\\n+)([^\\n]*)")
private val BREAKS = Regex(" [^ ]")
private val LEADING_SPACE = Regex("^\\n* ")
private val SCIENTIFIC_WITHOUT_DOT = Regex("^[-+]?[0-9]+e")
private val DEPRECATED_BOOLEANS = setOf(
    "y", "Y", "yes", "Yes", "YES", "on", "On", "ON", "n", "N", "no", "No", "NO", "off", "Off", "OFF",
)
private val DEPRECATED_BASE60 = Regex("^[-+]?[0-9_]+(?::[0-9_]+)+(?:\\.[0-9_]*)?$")
private val YAML_NULLS = setOf("~", "null", "Null", "NULL")
private val YAML_BOOLEANS = setOf("true", "True", "TRUE", "false", "False", "FALSE")
private val YAML_FLOAT = Regex(
    "^(?:[-+]?(?:[0-9][0-9_]*)(?:\\.[0-9_]*)?(?:[eE][-+]?[0-9]+)?" +
        "|\\.[0-9_]+(?:[eE][-+]?[0-9]+)?" +
        "|[-+]?\\.(?:inf|Inf|INF)" +
        "|\\.(?:nan|NaN|NAN))$",
)
private val YAML_DATE = Regex("^([0-9][0-9][0-9][0-9])-([0-9][0-9])-([0-9][0-9])$")
private val YAML_TIMESTAMP = Regex(
    "^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:[Tt]|[ \\t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])" +
        "(?:\\.([0-9]*))?(?:[ \\t]*(Z|([-+])([0-9][0-9]?)(?::([0-9][0-9]))?))?$",
)
private val YAML_ESCAPES = mapOf(
    0x00 to "\\0", 0x07 to "\\a", 0x08 to "\\b", 0x09 to "\\t", 0x0A to "\\n", 0x0B to "\\v", 0x0C to "\\f",
    0x0D to "\\r", 0x1B to "\\e", 0x22 to "\\\"", 0x5C to "\\\\", 0x85 to "\\N", 0xA0 to "\\_",
    0x2028 to "\\L", 0x2029 to "\\P",
)
