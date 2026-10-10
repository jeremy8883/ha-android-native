package io.homeassistant.companion.android.dashboard.theme

import io.homeassistant.companion.android.dashboard.derive.DisplayColor

/**
 * The ARGB of [color] with the frontend's default theme (light, or [dark]), or `null` when it doesn't resolve:
 * then the element's own colour shows, as CSS falls back to the inherited colour. A state colour's
 * [DisplayColor.State.overrides] replace its variables, and an [DisplayColor.State.unset] variable ends the
 * lookup unresolved, as a variable set to `initial` makes the whole `var()` chain invalid.
 */
fun resolveDisplayColor(color: DisplayColor, dark: Boolean): Long? = when (color) {
    is DisplayColor.Theme -> themeColorArgb("${color.name}-color", dark)
    is DisplayColor.Literal -> parseCssColor(color.css)
    is DisplayColor.State -> resolveState(color, dark)
}

private fun resolveState(color: DisplayColor.State, dark: Boolean): Long? =
    // An unset variable ends the lookup: the ones after it are never reached
    color.variables.takeWhile { it !in color.unset }.firstNotNullOfOrNull { variable ->
        color.overrides[variable]?.let { resolveDisplayColor(it, dark) } ?: themeColorArgb(variable, dark)
    }

/** `#rgb`, `#rrggbb` and `rgb(r, g, b)` colours as ARGB; others are not resolved. */
fun parseCssColor(css: String): Long? {
    val text = css.trim()
    return HEX.matchEntire(text)?.let { hexColor(it.groupValues[1]) } ?: RGB.matchEntire(text)?.let(::rgbColor)
}

private fun hexColor(hex: String): Long {
    val digits = if (hex.length == SHORT_HEX) hex.map { c -> "$c$c" }.joinToString("") else hex
    return OPAQUE or digits.toLong(HEX_RADIX)
}

private fun rgbColor(match: MatchResult): Long {
    val (r, g, b) = match.destructured.toList().map { it.toLong().coerceIn(0, CHANNEL_MAX) }
    return OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
}

private const val SHORT_HEX = 3
private const val HEX_RADIX = 16
private const val CHANNEL_MAX = 255L
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = 0xFF000000
private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
private val RGB = Regex("""^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)$""")
