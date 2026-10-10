package io.homeassistant.companion.android.dashboard.display

/**
 * A formatted value with its [unit] apart (port of `ValuePart`s): [unitFirst] when the unit comes before the
 * value ("$5"), and [separator] the space between them.
 */
data class ValueParts(
    val value: String,
    val unit: String? = null,
    val unitFirst: Boolean = false,
    val separator: String = "",
) {
    /** The value and unit together, as one text. */
    val text: String
        get() = when {
            unit == null -> value
            unitFirst -> unit + separator + value
            else -> value + separator + unit
        }
}

/** A formatted amount ("-$12.00", "12,00 €") as its number and its currency symbol, in their order. */
internal fun currencyParts(formatted: String): ValueParts {
    val number = AMOUNT.find(formatted)
    // A sign before the symbol ("-$12.00") stays with the number, as upstream joins the value parts
    val sign = formatted.takeWhile { it in SIGNS }.takeIf { number != null && number.range.first > it.length }.orEmpty()
    val before = number?.let { formatted.substring(sign.length, it.range.first) }.orEmpty()
    val after = number?.let { formatted.substring(it.range.last + 1) }.orEmpty()
    val unitFirst = before.isNotBlank()
    val unit = (if (unitFirst) before else after).trim()
    val separator = if (unitFirst) before.removePrefix(before.trimEnd()) else after.removeSuffix(after.trimStart())
    return if (number == null || unit.isEmpty()) {
        ValueParts(formatted)
    } else {
        ValueParts(sign + number.value, unit, unitFirst, separator)
    }
}

private const val SIGNS = "-\u2212+"

/** The signed number in a formatted amount. */
private val AMOUNT = Regex("[-\u2212+]?\\d[\\d.,\\u00a0\\u202f\\u2019' ]*\\d|[-\u2212+]?\\d")
