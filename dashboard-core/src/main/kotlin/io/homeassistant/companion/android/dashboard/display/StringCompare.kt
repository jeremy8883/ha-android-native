package io.homeassistant.companion.android.dashboard.display

import java.math.BigInteger
import java.text.Normalizer

// How the frontend orders names: `caseInsensitiveStringCompare` (frontend@20260624.6 src/common/string/compare.ts),
// an `Intl.Collator` with `{sensitivity: "accent", numeric: true}`. The JDK's collator ignores spaces and punctuation,
// which the browser's (ICU, CLDR root order) doesn't, so the order is computed here: spaces, then punctuation, then
// symbols, then numbers (by value), then letters (alphabetically); case never counts, accents only to break ties.

/** Port of `caseInsensitiveStringCompare`: negative when [a] goes before [b], 0 when they're equal but for case. */
fun caseInsensitiveStringCompare(a: String, b: String): Int {
    val keysA = collationKeys(a)
    val keysB = collationKeys(b)
    val pairs = keysA.zip(keysB)
    // Equal but for accents (or case): accents decide
    return pairs.firstNotNullOfOrNull { (x, y) -> x.comparePrimary(y).takeIf { it != 0 } }
        ?: keysA.size.compareTo(keysB.size).takeIf { it != 0 }
        ?: pairs.firstNotNullOfOrNull { (x, y) -> x.accented.compareTo(y.accented).takeIf { it != 0 } }
        ?: 0
}

/**
 * Port of `stringCompare` (an `Intl.Collator` with `{numeric: true}`): as [caseInsensitiveStringCompare], with lower
 * case before upper case to break ties.
 */
fun stringCompare(a: String, b: String): Int = caseInsensitiveStringCompare(a, b).takeIf { it != 0 }
    ?: a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, _) -> if (x.isLowerCase()) -1 else 1 }
    ?: 0

/** A comparator of [caseInsensitiveStringCompare], for `sortedWith`. */
val CaseInsensitiveOrder: Comparator<String> = Comparator(::caseInsensitiveStringCompare)

/** One unit of a name as the collator weighs it: its [group], its [base] text or [number], and its [accented] form. */
private class CollationKey(val group: Int, val base: String, val number: BigInteger?, val accented: String) {
    fun comparePrimary(other: CollationKey): Int = when {
        group != other.group -> group.compareTo(other.group)
        number != null && other.number != null -> number.compareTo(other.number)
        else -> base.compareTo(other.base)
    }
}

private fun collationKeys(text: String): List<CollationKey> {
    val keys = mutableListOf<CollationKey>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char.isDigit()) {
            var end = index
            while (end < text.length && text[end].isDigit()) end++
            val digits = text.substring(index, end)
            keys += CollationKey(GROUP_DIGIT, digits, digits.toBigInteger(), digits)
            index = end
        } else {
            val lower = char.lowercaseChar().toString()
            keys += CollationKey(groupOf(char), stripAccents(lower), null, lower)
            index++
        }
    }
    return keys
}

private fun groupOf(char: Char): Int = when {
    char.isWhitespace() -> GROUP_SPACE
    char.isLetter() -> GROUP_LETTER
    Character.getType(char) in SYMBOL_TYPES -> GROUP_SYMBOL
    else -> GROUP_PUNCTUATION
}

private fun stripAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

private val COMBINING_MARKS = Regex("\\p{M}+")
private val SYMBOL_TYPES = setOf(
    Character.MATH_SYMBOL.toInt(),
    Character.CURRENCY_SYMBOL.toInt(),
    Character.MODIFIER_SYMBOL.toInt(),
    Character.OTHER_SYMBOL.toInt(),
)
private const val GROUP_SPACE = 0
private const val GROUP_PUNCTUATION = 1
private const val GROUP_SYMBOL = 2
private const val GROUP_DIGIT = 3
private const val GROUP_LETTER = 4
