package io.homeassistant.companion.android.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.theme.resolveDisplayColor
import io.homeassistant.companion.android.dashboard.theme.themeColorArgb

/**
 * The Compose colour of a [DisplayColor] with the frontend's default theme (light or dark like the system),
 * or `null` when it doesn't resolve (see [resolveDisplayColor]).
 */
@Composable
internal fun DisplayColor.toColor(): Color? = resolveDisplayColor(this, isSystemInDarkTheme())?.let(::Color)

/** The value of a frontend colour variable, following references to other variables. */
internal fun resolveVariable(name: String, dark: Boolean): Color? = themeColorArgb(name, dark)?.let(::Color)

/** `#rgb`, `#rrggbb` and `rgb(r, g, b)` colours; others are not resolved. */
internal fun parseCssColor(css: String): Color? =
    io.homeassistant.companion.android.dashboard.theme.parseCssColor(css)?.let(::Color)

/**
 * The tint of an entity icon drawn like `state-badge`: its [color], else the unavailable colour when [unavailable],
 * else `--state-icon-color`, dimmed by a light's [brightness] (CSS `brightness()`).
 */
@Composable
internal fun entityIconTint(color: DisplayColor?, unavailable: Boolean = false, brightness: Double? = null): Color {
    val base = color?.toColor()
        ?: DisplayColor.State(listOf(if (unavailable) UNAVAILABLE_COLOR else ICON_COLOR)).toColor()
        ?: Color.Gray
    return base.dimmed(brightness)
}

/** This colour through CSS `brightness([factor])`: each channel scaled, clamped. */
internal fun Color.dimmed(factor: Double?): Color {
    val scale = factor?.toFloat() ?: return this
    return copy(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
    )
}

private const val ICON_COLOR = "state-icon-color"
private const val UNAVAILABLE_COLOR = "state-unavailable-color"
