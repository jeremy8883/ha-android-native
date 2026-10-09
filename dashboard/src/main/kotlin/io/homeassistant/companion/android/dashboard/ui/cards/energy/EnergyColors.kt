package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.homeassistant.companion.android.dashboard.display.shadeRgb
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/** The frontend's colour variable of each kind of energy series (`colorPropertyMap` of the energy graph cards). */
internal val ENERGY_KIND_COLORS = mapOf(
    "to_grid" to "energy-grid-return-color",
    "to_battery" to "energy-battery-in-color",
    "from_grid" to "energy-grid-consumption-color",
    "used_grid" to "energy-grid-consumption-color",
    "used_solar" to "energy-solar-color",
    "used_battery" to "energy-battery-out-color",
)

/**
 * The colour of an energy series: the theme's colour of [variable], darker (lighter in [dark] mode) for each
 * [index] after the first, translucent as a bar's fill ([background]) and more so for the compared period. Port of
 * `getEnergyColor` (frontend@20260624.6 src/panels/lovelace/cards/energy/common/color.ts).
 */
internal fun energyColor(variable: String, dark: Boolean, index: Int?, background: Boolean, compare: Boolean): Color {
    val base = resolveVariable(variable, dark) ?: Color.Gray
    val shaded = if (index != null && index > 0) {
        Color(OPAQUE or shadeRgb(base.toArgb() and RGB, index, brighten = dark))
    } else {
        base
    }
    val alpha = when {
        compare && background -> COMPARE_FILL
        compare || background -> HALF
        else -> return shaded
    }
    return shaded.copy(alpha = alpha / ALPHA_MAX)
}

private const val OPAQUE = 0xFF shl 24
private const val RGB = 0xFFFFFF
private const val COMPARE_FILL = 0x32
private const val HALF = 0x7F
private const val ALPHA_MAX = 255f
