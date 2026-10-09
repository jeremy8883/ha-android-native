package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * What the energy distribution card shows: the energy of each source and of the home, and how it flowed between
 * them. Amounts are formatted, the electricity ones in one unit. Port of the computations of
 * `HuiEnergyDistrubutionCard.render` (frontend@20260624.6 src/panels/lovelace/cards/energy/hui-energy-distribution-card.ts).
 *
 * @property lowCarbon the grid's low-carbon energy, with a CO2 signal
 * @property flows the seconds each flow's dot takes along its line, by flow; absent flows have no dots
 * @property homeRing the shares of the home's consumption, drawn around the home
 * @property homeLabel the home's name, unless gas and water are both shown below it
 */
data class EnergyDistributionModel(
    val lowCarbon: String?,
    val solar: String?,
    val gas: String?,
    val water: String?,
    val grid: GridNode?,
    val home: String,
    val homeLabel: String?,
    val battery: BatteryNode?,
    val homeRing: HomeRing?,
    val flows: Map<DistributionFlow, Double>,
    val gasFlows: Boolean,
    val waterFlows: Boolean,
) {
    /** The water circle sits below the home, next to the battery, when gas takes its place above. */
    val waterBelow: Boolean get() = gas != null && water != null
}

/** The grid circle: what came from it, and what went back when there is an export meter. */
data class GridNode(val fromGrid: String, val returned: String?)

/** The battery circle: its icon (by charge when the period includes now), charge, and energy in and out. */
data class BatteryNode(val icon: String, val stateOfCharge: String?, val charged: String, val discharged: String)

/**
 * The shares of the home's consumption around it, as fractions of the circle: solar, battery and low-carbon
 * end at the top-right going counter-clockwise; the grid (high-carbon with a CO2 signal) starts there.
 */
data class HomeRing(val solar: Double?, val battery: Double?, val lowCarbon: Double?, val grid: Double?)

/** The flows the card animates. */
enum class DistributionFlow {
    SOLAR_TO_GRID,
    SOLAR_TO_HOME,
    GRID_TO_HOME,
    SOLAR_TO_BATTERY,
    BATTERY_TO_HOME,
    GRID_TO_BATTERY,
    BATTERY_TO_GRID,
}

/** The distribution of [data] at [now]. */
fun HassSnapshot.energyDistribution(data: EnergyData, now: Instant): EnergyDistributionModel {
    val kinds = SourceKinds(data.prefs)
    val amounts = data.summed().let { sums -> Amounts(sums, sums.consumption().total, kinds) }
    val used = amounts.used
    val carbon = if (kinds.grid) lowCarbon(data, amounts, used) else null
    val unit = formats.consumptionUnit(amounts.largest(carbon?.lowCarbon))
    fun kwh(value: Double?) = formats.consumptionShort(value, "kWh", unit)
    val gas = utilityUsage(data, UtilityType.GAS)
    val water = utilityUsage(data, UtilityType.WATER)
    return EnergyDistributionModel(
        lowCarbon = carbon?.lowCarbon?.let(::kwh),
        solar = amounts.solar?.let(::kwh),
        gas = gas?.let { formats.consumptionShort(it, data.gasUnit) },
        water = water?.let { formats.consumptionShort(it, data.waterUnit) },
        grid = if (kinds.grid) GridNode(kwh(amounts.fromGrid), amounts.returned?.let(::kwh)) else null,
        home = kwh(amounts.home),
        homeLabel = config.locationName.takeUnless { gas != null && water != null },
        battery = if (kinds.battery) battery(data, amounts, now, ::kwh) else null,
        homeRing = homeRing(used, amounts.home, kinds.grid, carbon),
        flows = flows(kinds, amounts.consumption, used),
        gasFlows = (gas ?: 0.0) != 0.0,
        waterFlows = (water ?: 0.0) != 0.0,
    )
}

/** Which sources the card shows. */
private class SourceKinds(prefs: EnergyPreferences) {
    private val grids = prefs.energySources.filterIsInstance<EnergySource.Grid>()
    val grid =
        grids.firstOrNull()?.let { !it.statEnergyFrom.isNullOrEmpty() || !it.statEnergyTo.isNullOrEmpty() } == true
    val solar = prefs.energySources.any { it is EnergySource.Solar }
    val battery = prefs.energySources.any { it is EnergySource.Battery }
    val returns = grids.any { !it.statEnergyTo.isNullOrEmpty() }
}

/** The totals of the period, `null` for sources that aren't configured. */
private class Amounts(sums: EnergySums, val consumption: Consumption, kinds: SourceKinds) {
    private fun total(sums: EnergySums, flow: EnergyFlow) = sums.total[flow] ?: 0.0

    val fromGrid = total(sums, EnergyFlow.FROM_GRID)
    val solar = total(sums, EnergyFlow.SOLAR).takeIf { kinds.solar }
    val batteryIn = total(sums, EnergyFlow.TO_BATTERY).takeIf { kinds.battery }
    val batteryOut = total(sums, EnergyFlow.FROM_BATTERY).takeIf { kinds.battery }
    val returned = total(sums, EnergyFlow.TO_GRID).takeIf { kinds.returns }
    val home = max(0.0, consumption.usedTotal)

    /** What the home used of each source. */
    val used = Used(
        solar = consumption.usedSolar.takeIf { kinds.solar },
        battery = max(consumption.usedBattery, 0.0).takeIf { kinds.battery },
        grid = if (kinds.grid) max(consumption.usedGrid, 0.0) else 0.0,
    )

    /** The largest amount, which picks the unit they're all shown in. */
    fun largest(lowCarbon: Double?) =
        listOfNotNull(lowCarbon, solar, returned, fromGrid, home, batteryIn, batteryOut).maxOrNull() ?: 0.0
}

/** What the home used of each source. */
private class Used(val solar: Double?, val battery: Double?, val grid: Double)

/** The low-carbon energy from the grid, and the high-carbon share of the home's consumption. */
private class Carbon(val lowCarbon: Double, val highCarbonConsumption: Double)

private fun lowCarbon(data: EnergyData, amounts: Amounts, used: Used): Carbon? {
    val highCarbon = data.fossilEnergyConsumption?.values?.sum()
    if (data.co2SignalEntity == null || highCarbon == null) return null
    // Only the part that the home used, not what charged the battery
    val consumed = if (used.grid != amounts.fromGrid) highCarbon * (used.grid / amounts.fromGrid) else highCarbon
    return Carbon(amounts.fromGrid - highCarbon, consumed)
}

private fun homeRing(used: Used, home: Double, hasGrid: Boolean, carbon: Carbon?): HomeRing? {
    val solar = used.solar?.let { it / home }
    val battery = used.battery?.takeIf { it != 0.0 }?.let { it / home }
    val highCarbon = carbon?.let { it.highCarbonConsumption / home }
    val lowCarbon = highCarbon?.let { 1 - (solar ?: 0.0) - (battery ?: 0.0) - it }
    if (solar == null && lowCarbon == null) return null
    val grid = when {
        !hasGrid -> null
        highCarbon != null -> highCarbon
        else -> 1 - (solar ?: 0.0) - (battery ?: 0.0)
    }
    return HomeRing(solar, battery, lowCarbon?.takeIf { it != 0.0 }, grid)
}

/**
 * The seconds each flow's dot takes along its line: faster with more of the total. Port of the `animateMotion`
 * durations.
 */
private fun flows(kinds: SourceKinds, consumption: Consumption, used: Used): Map<DistributionFlow, Double> {
    val hasGrid = kinds.grid
    val hasSolar = kinds.solar
    val hasBattery = kinds.battery
    val amounts = mapOf(
        DistributionFlow.SOLAR_TO_GRID to consumption.solarToGrid.takeIf { hasSolar && hasGrid },
        DistributionFlow.SOLAR_TO_HOME to used.solar,
        DistributionFlow.GRID_TO_HOME to used.grid,
        DistributionFlow.SOLAR_TO_BATTERY to consumption.solarToBattery.takeIf { hasSolar && hasBattery },
        DistributionFlow.BATTERY_TO_HOME to used.battery,
        DistributionFlow.GRID_TO_BATTERY to consumption.gridToBattery.takeIf { hasBattery && hasGrid },
        DistributionFlow.BATTERY_TO_GRID to consumption.batteryToGrid.takeIf { hasBattery && hasGrid },
    )
    val totalLines = amounts.values.sumOf { it ?: 0.0 }
    return amounts.mapNotNull { (flow, amount) ->
        // Solar to grid slows down more than the others, as upstream
        val slowdown = if (flow == DistributionFlow.SOLAR_TO_GRID) MAX_SECONDS else MAX_SECONDS - 1
        amount?.takeIf { it != 0.0 }?.let { flow to MAX_SECONDS - it / totalLines * slowdown }
    }.toMap()
}

private fun HassSnapshot.battery(
    data: EnergyData,
    amounts: Amounts,
    now: Instant,
    kwh: (Double?) -> String,
): BatteryNode {
    // The charge is the battery's current one, so only shown when the period includes now
    val includesNow = !data.period.endInstant(formats.zone).isBefore(now)
    val batteries = data.prefs.energySources.filterIsInstance<EnergySource.Battery>()
    val levels = if (includesNow) {
        batteries.mapNotNull {
            it.statSoc
        }.map { id -> states[id]?.state?.let(::jsNumber) ?: Double.NaN }.filter { it.isFinite() }
    } else {
        emptyList()
    }
    val average = levels.takeIf { it.isNotEmpty() }?.average()
    return BatteryNode(
        icon = average?.let(::batteryLevelIcon) ?: BATTERY_HIGH,
        stateOfCharge = average?.let { formats.number(BigDecimal.valueOf(it), 0, 0) + " %" },
        charged = kwh(amounts.batteryIn),
        discharged = kwh(amounts.batteryOut),
    )
}

/** The usage of the gas or water sources, `null` without one; 0 when they didn't change. */
private fun utilityUsage(data: EnergyData, type: UtilityType): Double? = data.prefs.energySources
    .filterIsInstance<EnergySource.Utility>().filter { it.type == type }.map { it.statEnergyFrom }
    .takeIf { it.isNotEmpty() }
    ?.let { statisticsSumGrowth(data.stats, it) ?: 0.0 }

/** The battery icon for a charge level. Port of `batteryLevelIconPath` (src/common/entity/battery_icon.ts). */
fun batteryLevelIcon(level: Double): String = when {
    level.isNaN() -> "mdi:battery-unknown"
    level <= BATTERY_ALERT_LEVEL -> "mdi:battery-alert-variant-outline"
    else -> when (val rounded = (level / TEN).roundToLong() * TEN.toLong()) {
        FULL -> "mdi:battery"
        else -> "mdi:battery-$rounded"
    }
}

private const val MAX_SECONDS = 6.0
private const val BATTERY_HIGH = "mdi:battery-high"
private const val BATTERY_ALERT_LEVEL = 5
private const val TEN = 10.0
private const val FULL = 100L
