package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import java.math.BigDecimal
import kotlin.math.floor

/** Port of `getNumberFormatOptions` (src/common/number/format_number.ts) as (minimum, maximum) fraction digits. */
internal fun numberFormatOptions(state: EntityState, value: String, entry: EntityEntry?): Pair<Int?, Int?> {
    entry?.displayPrecision?.let { return it to it }
    val step = jsNumber(state.attributes["step"])
    return if (step.isWholeNumber() && jsNumber(value).isWholeNumber()) null to 0 else null to null
}

/**
 * Port of `formatNumber` for a string value: digits of the string are kept unless fraction digits are given, at
 * most 2 by default. Values that are not numbers are returned unchanged.
 */
internal fun HassSnapshot.formatNumber(value: String, minDigits: Int?, maxDigits: Int?): String {
    val number = jsNumber(value)
    if (number.isNaN() || number.isInfinite()) return value
    var min = minDigits ?: 0
    var max = maxDigits ?: DEFAULT_MAX_FRACTION_DIGITS
    if (minDigits == null && maxDigits == null) {
        val digits = if ('.' in value) value.substringAfter('.').length else 0
        min = digits
        max = digits
    }
    return formats.number(BigDecimal.valueOf(number), min, maxOf(min, max))
}

/** Port of `formatNumber` for a number value (an attribute): at most 2 fraction digits. */
internal fun HassSnapshot.formatNumber(value: Double): String =
    if (value.isNaN() || value.isInfinite()) jsNumberString(value) else formats.number(BigDecimal.valueOf(value), 0, 2)

/** Port of `blankBeforeUnit` (src/common/translations/blank_before_unit.ts) for English. */
fun blankBeforeUnit(unit: String): String = if (unit == "°" || unit == "%") "" else " "

internal fun Double.isWholeNumber(): Boolean = !isNaN() && !isInfinite() && this == floor(this)

/** JavaScript `Math.round`. */
internal fun jsRound(value: Double): Long = floor(value + HALF).toLong()

/** Port of `round`: [value] rounded to [precision] decimals the way upstream does it. */
internal fun jsRoundTo(value: Double, precision: Int): Double {
    val factor = Math.pow(TEN, precision.toDouble())
    return floor(value * factor + HALF) / factor
}

/** JavaScript `String(number)` for whole and simple decimal values. */
internal fun jsNumberString(value: Double): String = when {
    value.isNaN() -> "NaN"
    value.isInfinite() -> if (value > 0) "Infinity" else "-Infinity"
    value.isWholeNumber() -> value.toLong().toString()
    else -> value.toString()
}

private const val DEFAULT_MAX_FRACTION_DIGITS = 2
private const val HALF = 0.5
private const val TEN = 10.0
