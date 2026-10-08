package io.homeassistant.companion.android.dashboard.entity

/**
 * Format an ICU MessageFormat [pattern] the way the frontend's `intl-messageformat` does for the subset its
 * translations use: `{name}` arguments, `{n, plural, =0 {...} one {...} other {...}}` with `#` for the number,
 * and `{key, select, a {...} other {...}}`. Plural categories follow English (`one` for exactly 1). Unknown
 * arguments are left empty; malformed patterns are returned as they are.
 */
fun formatIcuMessage(pattern: String, args: Map<String, String>): String =
    runCatching { IcuParser(pattern, args).message(pound = null, untilBrace = false) }.getOrDefault(pattern)

private class IcuParser(private val text: String, private val args: Map<String, String>) {
    private var pos = 0

    /** Text up to the end, or up to the closing brace of the enclosing option when [untilBrace]. */
    fun message(pound: String?, untilBrace: Boolean): String {
        val out = StringBuilder()
        while (pos < text.length) {
            val char = text[pos]
            when {
                char == '}' && untilBrace -> return out.toString()
                char == '{' -> {
                    pos++
                    out.append(argument(pound))
                }
                char == '#' && pound != null -> {
                    pos++
                    out.append(pound)
                }
                char == '\'' && text.startsWith("''", pos) -> {
                    pos += 2
                    out.append('\'')
                }
                else -> {
                    pos++
                    out.append(char)
                }
            }
        }
        require(!untilBrace) { "Unclosed option" }
        return out.toString()
    }

    /** The text of an argument, after its opening brace; consumes up to its closing brace. */
    private fun argument(pound: String?): String {
        val name = token()
        skipSpaces()
        if (peek() == '}') {
            pos++
            return args[name].orEmpty()
        }
        expect(',')
        val type = token()
        skipSpaces()
        if (type != PLURAL && type != SELECT && type != SELECT_ORDINAL) {
            // {n, number} and similar: the value as given
            skipTo('}')
            pos++
            return args[name].orEmpty()
        }
        expect(',')
        val value = args[name].orEmpty()
        var offset = 0.0
        val options = linkedMapOf<String, String>()
        while (true) {
            skipSpaces()
            if (peek() == '}') {
                pos++
                break
            }
            val key = token()
            if (key.startsWith("offset:")) {
                offset = key.removePrefix("offset:").toDouble()
                continue
            }
            skipSpaces()
            expect('{')
            val number = value.toDoubleOrNull()?.minus(offset)
            val innerPound = if (type == SELECT) pound else number?.let(::formatPound) ?: value
            options[key] = message(innerPound, untilBrace = true)
            pos++
        }
        return when (type) {
            SELECT -> options[value] ?: options[OTHER].orEmpty()
            else -> {
                val number = value.toDoubleOrNull()
                val exact = number?.let { options["=${formatPound(it)}"] }
                exact ?: options[pluralCategory(number?.minus(offset))] ?: options[OTHER].orEmpty()
            }
        }
    }

    private fun token(): String {
        skipSpaces()
        val start = pos
        while (pos < text.length && !text[pos].isWhitespace() && text[pos] !in "{},") pos++
        return text.substring(start, pos)
    }

    private fun skipSpaces() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun skipTo(char: Char) {
        while (pos < text.length && text[pos] != char) pos++
        require(pos < text.length) { "Expected $char" }
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun expect(char: Char) {
        skipSpaces()
        require(peek() == char) { "Expected $char at $pos" }
        pos++
    }

    private companion object {
        const val PLURAL = "plural"
        const val SELECT = "select"
        const val SELECT_ORDINAL = "selectordinal"
        const val OTHER = "other"

        /** English cardinal plural rules: `one` for the integer 1, `other` otherwise. */
        fun pluralCategory(number: Double?): String = if (number == 1.0) "one" else OTHER

        fun formatPound(number: Double): String =
            if (number == Math.floor(number) && !number.isInfinite()) number.toLong().toString() else number.toString()
    }
}
