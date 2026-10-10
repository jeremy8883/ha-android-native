package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.theme.themeColorArgb

/**
 * The Compose colour of a [DisplayColor] with the frontend's default theme (light or dark like the system),
 * or `null` when it can't be resolved.
 */
@Composable
internal fun DisplayColor.toColor(): Color? = resolve(this, isSystemInDarkTheme())

/** [color] in the light or [dark] theme; a state colour's [DisplayColor.State.overrides] replace its variables. */
private fun resolve(color: DisplayColor, dark: Boolean): Color? = when (color) {
    is DisplayColor.Theme -> resolveVariable("${color.name}-color", dark)
    is DisplayColor.Literal -> parseCssColor(color.css)
    is DisplayColor.State -> color.variables.firstNotNullOfOrNull { variable ->
        color.overrides[variable]?.let { resolve(it, dark) } ?: resolveVariable(variable, dark)
    }
}

/** The value of a frontend colour variable, following references to other variables. */
internal fun resolveVariable(name: String, dark: Boolean): Color? = themeColorArgb(name, dark)?.let(::Color)

/** `#rgb`, `#rrggbb` and `rgb(r, g, b)` colours; others are not resolved. */
internal fun parseCssColor(css: String): Color? {
    val text = css.trim()
    return HEX.matchEntire(text)?.let { hexColor(it.groupValues[1]) } ?: RGB.matchEntire(text)?.let(::rgbColor)
}

private fun hexColor(hex: String): Color {
    val digits = if (hex.length == SHORT_HEX) hex.map { c -> "$c$c" }.joinToString("") else hex
    return Color(OPAQUE or digits.toLong(HEX_RADIX))
}

private fun rgbColor(match: MatchResult): Color {
    val (r, g, b) = match.destructured
    return Color(
        r.toInt().coerceIn(0, CHANNEL_MAX),
        g.toInt().coerceIn(0, CHANNEL_MAX),
        b.toInt().coerceIn(0, CHANNEL_MAX),
    )
}

private const val SHORT_HEX = 3
private const val HEX_RADIX = 16
private const val CHANNEL_MAX = 255
private const val OPAQUE = 0xFF000000
private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
private val RGB = Regex("""^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)$""")
