package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.homeassistant.companion.android.dashboard.display.shadeRgb
import io.homeassistant.companion.android.dashboard.history.TimelineColor
import io.homeassistant.companion.android.dashboard.ui.cards.energy.graphColorVariable
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * The colours of timeline bands. States without a colour of their own get the next graph palette colour the first
 * time one shows, and keep it in every timeline for the life of the app, like the frontend's (`stateColorMap`).
 */
internal object TimelineColors {
    private val generic = mutableMapOf<String, Int>()

    /** [color] in the default theme, light or [dark]. */
    fun resolve(color: TimelineColor, dark: Boolean): Color {
        val base = color.variables.firstNotNullOfOrNull { resolveVariable(it, dark) }
        val shade = color.shade
        return when {
            base != null && shade != null -> Color(OPAQUE or shadeRgb(base.toArgb() and RGB, shade, brighten = true))
            base != null -> base
            else -> {
                val index =
                    color.paletteIndex ?: synchronized(generic) { generic.getOrPut(color.state) { generic.size } }
                resolveVariable(graphColorVariable(index), dark) ?: Color.Gray
            }
        }
    }

    private const val OPAQUE = 0xFF shl 24
    private const val RGB = 0xFFFFFF
}
