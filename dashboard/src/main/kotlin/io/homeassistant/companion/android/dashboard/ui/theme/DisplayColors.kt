package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.dashboard.derive.DisplayColor

/**
 * The Compose colour of a [DisplayColor] with the frontend's default theme (light or dark like the system),
 * or `null` when it can't be resolved.
 */
@Composable
internal fun DisplayColor.toColor(): Color? {
    val dark = isSystemInDarkTheme()
    return when (this) {
        is DisplayColor.Theme -> resolveVariable("$name-color", dark)
        is DisplayColor.Literal -> parseCssColor(css)
        is DisplayColor.State -> variables.firstNotNullOfOrNull { resolveVariable(it, dark) }
    }
}

/** The value of a frontend colour variable, following references to other variables. */
internal fun resolveVariable(name: String, dark: Boolean): Color? {
    var variable = name
    repeat(MAX_REFERENCE_DEPTH) {
        val value =
            (if (dark) FRONTEND_DARK_COLORS[variable] else null) ?: FRONTEND_LIGHT_COLORS[variable] ?: return null
        when (value) {
            is FrontendColor.Hex -> return Color(value.argb)
            is FrontendColor.Ref -> variable = value.variable
        }
    }
    return null
}

/** `#rgb`, `#rrggbb` and `rgb(r, g, b)` colours; others are not resolved. */
internal fun parseCssColor(css: String): Color? {
    val text = css.trim()
    HEX.matchEntire(text)?.let { match ->
        val digits = match.groupValues[1].let {
            if (it.length ==
                SHORT_HEX
            ) {
                it.map { c -> "$c$c" }.joinToString("")
            } else {
                it
            }
        }
        return Color(OPAQUE or digits.toLong(HEX_RADIX))
    }
    RGB.matchEntire(text)?.let { match ->
        val (r, g, b) = match.destructured
        return Color(
            r.toInt().coerceIn(0, CHANNEL_MAX),
            g.toInt().coerceIn(0, CHANNEL_MAX),
            b.toInt().coerceIn(0, CHANNEL_MAX),
        )
    }
    return null
}

private const val MAX_REFERENCE_DEPTH = 8
private const val SHORT_HEX = 3
private const val HEX_RADIX = 16
private const val CHANNEL_MAX = 255
private const val OPAQUE = 0xFF000000
private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
private val RGB = Regex("""^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)$""")
