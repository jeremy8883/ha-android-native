package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.derive.jsParseFloat
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max

// Live power and flow rates read from entity states. Port of `getPowerFromState`, `getFlowRateFromState`,
// `computeTotalFlowRate`, `formatPowerShort` and `formatFlowRateShort` (frontend@20260624.6 src/data/energy.ts).

/** [entityId]'s current power in W, scaled by its unit's SI prefix; `null` when it has no numeric state. */
fun HassSnapshot.powerWatts(entityId: String): Double? =
    reading(entityId)?.let { (value, unit) -> normalizeBySiPrefix(value, unit) }

/** [entityId]'s current flow rate in L/min (as is for an unknown unit); `null` when it has no numeric state. */
fun HassSnapshot.flowRateLitresPerMinute(entityId: String): Double? =
    reading(entityId)?.let { (value, unit) -> value * (FLOW_RATE_TO_LMIN[unit] ?: 1.0) }

/** [entityId]'s state as a number (JavaScript's `parseFloat`) with its unit, `null` when it isn't one. */
private fun HassSnapshot.reading(entityId: String): Pair<Double, String?>? = states[entityId]?.let { state ->
    jsParseFloat(state.state).takeUnless { it.isNaN() }?.let { it to state.attributes.string("unit_of_measurement") }
}

/** A flow rate in a [unit] of measurement. */
data class FlowRate(val value: Double, val unit: String)

/**
 * The current total flow rate of the gas or water ([type]) sources, in the first source's unit; sources without a
 * numeric state or a unit are left out, and negative rates count as 0. Port of `computeTotalFlowRate`.
 */
fun HassSnapshot.totalFlowRate(prefs: EnergyPreferences, type: UtilityType): FlowRate {
    var unit: String? = null
    var total = 0.0
    prefs.energySources.filterIsInstance<EnergySource.Utility>().filter { it.type == type }.forEach { source ->
        val state = source.statRate?.ifEmpty { null }?.let { states[it] } ?: return@forEach
        val value = max(jsParseFloat(state.state).takeUnless { it.isNaN() } ?: return@forEach, 0.0)
        val entityUnit = state.attributes.string("unit_of_measurement")?.ifEmpty { null } ?: return@forEach
        val target = unit ?: entityUnit.also { unit = it }
        val from = FLOW_RATE_TO_LMIN[entityUnit]
        val to = FLOW_RATE_TO_LMIN[target]
        total += if (entityUnit != target && from != null && to != null) value * from / to else value
    }
    return FlowRate(max(0.0, total), unit.orEmpty())
}

/** [watts] in W, kW, MW… whichever keeps it under 1000: no decimals in W, up to 3 above. */
fun DisplayFormats.powerShort(watts: Double): String {
    var value = watts
    var index = 0
    while (abs(value) >= KILO && index < POWER_UNITS.lastIndex) {
        value /= KILO
        index++
    }
    val digits = if (index == 0) 0 else POWER_DIGITS
    return "${number(BigDecimal.valueOf(value), 0, digits)} ${POWER_UNITS[index]}"
}

/** [litresPerMinute] in L/min, or gal/min unless [metric], with up to one decimal. */
fun DisplayFormats.flowRateShort(litresPerMinute: Double, metric: Boolean): String {
    val (value, unit) = if (metric) litresPerMinute to "L/min" else litresPerMinute / LITERS_PER_GALLON to "gal/min"
    return "${number(BigDecimal.valueOf(value), 0, 1)} $unit"
}

/** The power the home uses right now in W, never below 0. Port of `_computeTotalPower` (hui-power-total-badge.ts). */
fun HassSnapshot.powerUse(prefs: EnergyPreferences): Double = powerFlows(prefs).usedTotal

/** The power total badge's text: [watts] in kW with up to 2 decimals from 1000 W, in W without decimals below. */
fun DisplayFormats.powerTotal(watts: Double): String = if (watts >= KILO) {
    "${number(BigDecimal.valueOf(watts / KILO), 0, 2)} kW"
} else {
    "${number(BigDecimal.valueOf(watts), 0, 0)} W"
}

/** The gas and water total badges' text: [rate] with up to one decimal and its unit. */
fun DisplayFormats.flowRate(rate: FlowRate): String = "${number(BigDecimal.valueOf(rate.value), 0, 1)} ${rate.unit}"

/** The current power flows, batteries netted as they only flow one way at a time. Port of `_computePowerData`. */
internal class PowerFlows(
    val solar: Double,
    val fromGrid: Double,
    val toGrid: Double,
    val fromBattery: Double,
    val toBattery: Double,
) {
    val consumption = computeConsumption(fromGrid, toGrid, solar, toBattery, fromBattery)
    val usedTotal = max(0.0, consumption.usedTotal)
}

internal fun HassSnapshot.powerFlows(prefs: EnergyPreferences): PowerFlows {
    fun power(sources: List<EnergySource>) = sources.mapNotNull { it.statRate?.ifEmpty { null } }
        .map { powerWatts(it) ?: 0.0 }
    val grid = power(prefs.energySources.filterIsInstance<EnergySource.Grid>())
    val battery = power(prefs.energySources.filterIsInstance<EnergySource.Battery>()).sum()
    return PowerFlows(
        solar = power(prefs.energySources.filterIsInstance<EnergySource.Solar>()).filter { it > 0 }.sum(),
        fromGrid = grid.filter { it > 0 }.sum(),
        toGrid = -grid.filter { it < 0 }.sum(),
        fromBattery = max(battery, 0.0),
        toBattery = max(-battery, 0.0),
    )
}

/** Port of `normalizeValueBySIPrefix`: only units longer than their prefix, so a bare "m" isn't milli. */
private fun normalizeBySiPrefix(value: Double, unit: String?): Double {
    if (unit == null || unit.length <= 1) return value
    return value * (SI_PREFIXES[unit[0]] ?: 1.0)
}

private const val POWER_DIGITS = 3
private const val LITERS_PER_GALLON = 3.785411784
private const val MINUTES_PER_HOUR = 60.0
private const val MINUTES_PER_DAY = 1440.0
private val POWER_UNITS = listOf("W", "kW", "MW", "GW", "TW")

private const val TERA = 1e12
private const val GIGA = 1e9
private const val MEGA = 1e6
private const val KILO = 1e3
private const val MILLI = 1e-3
private const val MICRO = 1e-6
private const val LITERS_PER_CUBIC_METER = 1000.0
private const val LITERS_PER_CUBIC_FOOT = 28.316846592
private const val SECONDS_PER_MINUTE = 60.0

private val SI_PREFIXES = mapOf(
    'T' to TERA,
    'G' to GIGA,
    'M' to MEGA,
    'k' to KILO,
    'm' to MILLI,
    '\u00B5' to MICRO,
    '\u03BC' to MICRO,
)

private val FLOW_RATE_TO_LMIN = mapOf(
    "m³/h" to LITERS_PER_CUBIC_METER / MINUTES_PER_HOUR,
    "m³/min" to LITERS_PER_CUBIC_METER,
    "m³/s" to LITERS_PER_CUBIC_METER * SECONDS_PER_MINUTE,
    "ft³/min" to LITERS_PER_CUBIC_FOOT,
    "L/h" to 1 / MINUTES_PER_HOUR,
    "L/min" to 1.0,
    "L/s" to SECONDS_PER_MINUTE,
    "gal/h" to LITERS_PER_GALLON / MINUTES_PER_HOUR,
    "gal/min" to LITERS_PER_GALLON,
    "gal/d" to LITERS_PER_GALLON / MINUTES_PER_DAY,
    "mL/s" to SECONDS_PER_MINUTE / LITERS_PER_CUBIC_METER,
)
