package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The sums the electricity cards share. Port of `getSummedData`, `computeConsumptionData` and their helpers
// (frontend@20260624.6 src/data/energy.ts), and of `calculateStatisticsSumGrowth` (src/data/recorder.ts).

/** The flows between the grid, solar and batteries, by period start (epoch ms). */
enum class EnergyFlow { TO_GRID, FROM_GRID, TO_BATTERY, FROM_BATTERY, SOLAR }

/**
 * The energy of each [EnergyFlow] configured, by period start and in total. A flow without a source is absent from
 * both, as upstream leaves it `undefined`. Port of `EnergySumData`.
 */
data class EnergySums(
    val byStart: Map<EnergyFlow, Map<Long, Double>>,
    val total: Map<EnergyFlow, Double>,
    val timestamps: List<Long>,
)

/** The sums of [EnergyData.stats], or of [EnergyData.statsCompare] when [compare]. Port of `getSummedDataPartial`. */
fun EnergyData.summed(compare: Boolean = false): EnergySums {
    val statistics = (if (compare) statsCompare else stats).orEmpty()
    val timestamps = sortedSetOf<Long>()
    val byStart = mutableMapOf<EnergyFlow, Map<Long, Double>>()
    val total = mutableMapOf<EnergyFlow, Double>()
    prefs.flowStatistics().forEach { (flow, ids) ->
        val sums = linkedMapOf<Long, Double>()
        var sum = 0.0
        ids.forEach { id ->
            statistics[id].orEmpty().forEach { stat ->
                val change = stat.change ?: return@forEach
                sum += change
                sums[stat.start] = (sums[stat.start] ?: 0.0) + change
                timestamps += stat.start
            }
        }
        byStart[flow] = sums
        total[flow] = sum
    }
    return EnergySums(byStart, total, timestamps.toList())
}

/** The statistics of each flow, for the flows with a source. */
private fun EnergyPreferences.flowStatistics(): Map<EnergyFlow, List<String>> {
    val ids = linkedMapOf<EnergyFlow, MutableList<String>>()
    fun add(flow: EnergyFlow, id: String) = ids.getOrPut(flow) { mutableListOf() }.add(id)
    energySources.forEach { source ->
        when (source) {
            is EnergySource.Solar -> add(EnergyFlow.SOLAR, source.statEnergyFrom)
            is EnergySource.Battery -> {
                add(EnergyFlow.TO_BATTERY, source.statEnergyTo)
                add(EnergyFlow.FROM_BATTERY, source.statEnergyFrom)
            }
            is EnergySource.Grid -> {
                source.statEnergyFrom?.takeIf { it.isNotEmpty() }?.let { add(EnergyFlow.FROM_GRID, it) }
                source.statEnergyTo?.takeIf { it.isNotEmpty() }?.let { add(EnergyFlow.TO_GRID, it) }
            }
            is EnergySource.Utility -> Unit
        }
    }
    return ids
}

/** Where the energy of a period went. Port of the result of `computeConsumptionSingle`. */
data class Consumption(
    val usedSolar: Double = 0.0,
    val usedGrid: Double = 0.0,
    val usedBattery: Double = 0.0,
    val usedTotal: Double = 0.0,
    val gridToBattery: Double = 0.0,
    val batteryToGrid: Double = 0.0,
    val solarToBattery: Double = 0.0,
    val solarToGrid: Double = 0.0,
) {
    operator fun plus(other: Consumption) = Consumption(
        usedSolar = usedSolar + other.usedSolar,
        usedGrid = usedGrid + other.usedGrid,
        usedBattery = usedBattery + other.usedBattery,
        usedTotal = usedTotal + other.usedTotal,
        gridToBattery = gridToBattery + other.gridToBattery,
        batteryToGrid = batteryToGrid + other.batteryToGrid,
        solarToBattery = solarToBattery + other.solarToBattery,
        solarToGrid = solarToGrid + other.solarToGrid,
    )
}

/** Where the energy went in each period and in total. Port of `EnergyConsumptionData`. */
data class ConsumptionData(val byStart: Map<Long, Consumption>, val total: Consumption)

/** Port of `computeConsumptionDataPartial`. */
fun EnergySums.consumption(): ConsumptionData {
    val byStart = timestamps.associateWith { t ->
        fun at(flow: EnergyFlow) = byStart[flow]?.get(t) ?: 0.0
        computeConsumption(
            fromGrid = at(EnergyFlow.FROM_GRID),
            toGrid = at(EnergyFlow.TO_GRID),
            solar = at(EnergyFlow.SOLAR),
            toBattery = at(EnergyFlow.TO_BATTERY),
            fromBattery = at(EnergyFlow.FROM_BATTERY),
        )
    }
    return ConsumptionData(byStart, byStart.values.fold(Consumption(), Consumption::plus))
}

/**
 * Where a period's energy went, in priority order: solar charges the battery, then goes to the grid; the battery
 * goes to the grid; the grid charges the battery; then solar, the battery and the grid supply the home. Grid
 * energy beyond the home's use charged the battery first. Port of `computeConsumptionSingle`.
 */
fun computeConsumption(
    fromGrid: Double,
    toGrid: Double,
    solar: Double,
    toBattery: Double,
    fromBattery: Double,
): Consumption {
    var gridOut = max(toGrid, 0.0)
    var batteryIn = max(toBattery, 0.0)
    var solarLeft = max(solar, 0.0)
    var gridIn = max(fromGrid, 0.0)
    var batteryOut = max(fromBattery, 0.0)
    val usedTotal = gridIn + solarLeft + batteryOut - gridOut - batteryIn
    var remaining = max(usedTotal, 0.0)

    val excessGridIn = max(0.0, min(batteryIn, gridIn - remaining))
    var gridToBattery = excessGridIn
    batteryIn -= excessGridIn
    gridIn -= excessGridIn

    val solarToBattery = min(solarLeft, batteryIn)
    batteryIn -= solarToBattery
    solarLeft -= solarToBattery

    val solarToGrid = min(solarLeft, gridOut)
    gridOut -= solarToGrid
    solarLeft -= solarToGrid

    val batteryToGrid = min(batteryOut, gridOut)
    batteryOut -= batteryToGrid

    val gridToBatterySecond = min(gridIn, batteryIn)
    gridToBattery += gridToBatterySecond
    gridIn -= gridToBatterySecond

    val usedSolar = min(remaining, solarLeft)
    remaining -= usedSolar
    val usedBattery = min(batteryOut, remaining)
    remaining -= usedBattery
    val usedGrid = min(remaining, gridIn)

    return Consumption(
        usedSolar = usedSolar,
        usedGrid = usedGrid,
        usedBattery = usedBattery,
        usedTotal = usedTotal,
        gridToBattery = gridToBattery,
        batteryToGrid = batteryToGrid,
        solarToBattery = solarToBattery,
        solarToGrid = solarToGrid,
    )
}

/**
 * The total change of [ids] in [statistics], `null` when none of them changed in the period. Port of
 * `calculateStatisticsSumGrowth`.
 */
fun statisticsSumGrowth(statistics: Statistics, ids: List<String>): Double? = ids
    .mapNotNull { id -> statistics[id]?.mapNotNull { it.change }?.takeIf { it.isNotEmpty() }?.sum() }
    .takeIf { it.isNotEmpty() }?.sum()

/**
 * An amount of energy in the unit that suits it ("1.23 kWh", "512 Wh"), or in [targetUnit]; other units than Wh to
 * TWh are kept. Port of `formatConsumptionShort`.
 */
fun DisplayFormats.consumptionShort(consumption: Double?, unit: String, targetUnit: String? = null): String {
    var value = consumption ?: 0.0
    var index = ENERGY_UNITS.indexOf(unit)
    val target = targetUnit?.let(ENERGY_UNITS::indexOf) ?: -1
    var picked = unit
    if (index >= 0) {
        while (if (target > -1) target < index else abs(value) < 1 && index > 0) {
            value *= UNIT_STEP
            index--
        }
        while (if (target > -1) target > index else abs(value) >= UNIT_STEP && index < ENERGY_UNITS.size - 1) {
            value /= UNIT_STEP
            index++
        }
        picked = ENERGY_UNITS[index]
    }
    val digits = when {
        abs(value) < TWO_DIGITS_BELOW -> 2
        abs(value) < ONE_DIGIT_BELOW -> 1
        else -> 0
    }
    return number(BigDecimal.valueOf(value), 0, digits) + " " + picked
}

/** The unit [consumptionShort] picks for [consumption] kWh. */
fun DisplayFormats.consumptionUnit(consumption: Double): String =
    consumptionShort(consumption, "kWh").substringAfterLast(' ')

private val ENERGY_UNITS = listOf("Wh", "kWh", "MWh", "GWh", "TWh")
private const val UNIT_STEP = 1000.0
private const val TWO_DIGITS_BELOW = 10
private const val ONE_DIGIT_BELOW = 100
