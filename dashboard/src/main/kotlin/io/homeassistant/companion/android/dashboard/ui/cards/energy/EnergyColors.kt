package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.homeassistant.companion.android.dashboard.display.shadeRgb
import io.homeassistant.companion.android.dashboard.energy.DEVICE_KIND
import io.homeassistant.companion.android.dashboard.energy.EnergyBarSeries
import io.homeassistant.companion.android.dashboard.energy.UNTRACKED_KIND
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/** The frontend's colour variable of each kind of energy series (`colorPropertyMap` of the energy graph cards). */
internal val ENERGY_KIND_COLORS = mapOf(
    "to_grid" to "energy-grid-return-color",
    "to_battery" to "energy-battery-in-color",
    "from_grid" to "energy-grid-consumption-color",
    "used_grid" to "energy-grid-consumption-color",
    "used_solar" to "energy-solar-color",
    "used_battery" to "energy-battery-out-color",
    "gas" to "energy-gas-color",
    "water" to "energy-water-color",
    "solar" to "energy-solar-color",
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

/**
 * The colour of [series]' bars: its kind's energy colour, a device's graph palette colour (`getGraphColorByIndex`),
 * or the unknown colour for the untracked consumption; `null` for an unknown kind.
 */
internal fun seriesColor(series: EnergyBarSeries, dark: Boolean, background: Boolean): Color? = when (series.kind) {
    DEVICE_KIND -> series.colorIndex?.let {
        energyColor(graphColorVariable(it), dark, null, background, series.compare)
    }
    UNTRACKED_KIND -> energyColor(UNTRACKED_COLOR, dark, null, background, series.compare)
    else -> ENERGY_KIND_COLORS[series.kind]?.let {
        energyColor(it, dark, series.colorIndex, background, series.compare)
    }
}

/** The graph palette's colour variable for [index], wrapping around its 54 colours. */
internal fun graphColorVariable(index: Int) = "color-${index % GRAPH_COLORS + 1}"

/** The colour of what no device accounts for (`--history-unknown-color`). */
internal const val UNTRACKED_COLOR = "history-unknown-color"
private const val GRAPH_COLORS = 54
