package io.homeassistant.companion.android.dashboard.energy

import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant
import kotlin.math.abs

/** The name upstream gives the energy data it downloads. */
const val ENERGY_CSV_FILE = "energy.csv"

/**
 * The energy data of [data] as the CSV "Download data" saves: a row per statistic of each source and device (and
 * their costs, in [currency]), by period, then the consumption computed from them. Port of `downloadEnergyData`
 * (src/data/energy.ts).
 */
fun energyCsv(data: EnergyData, currency: String?): String {
    val stats = data.stats
    // Every period any statistic has, oldest first
    val times = stats.values.flatMap { values -> values.map { it.start } }.toSortedSet().toList()
    val csv = StringBuilder()
    csv.append("entity_id,type,unit,").append(times.joinToString(",") { isoString(Instant.ofEpochMilli(it)) })
        .append('\n')
    fun row(id: String, type: String, unit: String, values: List<StatisticValue>) {
        var n = 0
        val cells = mutableListOf(id, type, Normalizer.normalize(unit, Normalizer.Form.NFKD))
        times.forEach { time ->
            if (n < values.size && values[n].start == time) {
                cells += values[n].change?.let(::jsString).orEmpty()
                n++
            } else {
                cells += ""
            }
        }
        csv.append(cells.joinToString(",")).append('\n')
    }
    fun category(
        type: String,
        ids: List<String>,
        unit: String,
        costType: String? = null,
        costIds: List<String> = emptyList(),
    ) {
        ids.forEach { id -> stats[id]?.let { row(id, type, unit, it) } }
        if (costType != null) costIds.forEach { id -> stats[id]?.let { row(id, costType, currency.orEmpty(), it) } }
    }
    val sources = CsvSources(data)
    category("grid_consumption", sources.gridIn, KWH, "grid_consumption_cost", sources.gridInCost)
    category("grid_return", sources.gridOut, KWH, "grid_return_compensation", sources.gridOutCost)
    category("battery_in", sources.batteryIn, KWH)
    category("battery_out", sources.batteryOut, KWH)
    category("solar_production", sources.solar, KWH)
    category("gas_consumption", sources.gas, data.gasUnit, "gas_consumption_cost", sources.gasCost)
    category("water_consumption", sources.water, data.waterUnit, "water_consumption_cost", sources.waterCost)
    category("device_consumption", data.prefs.deviceConsumption.map { it.statConsumption }, KWH)
    category("device_consumption_water", data.prefs.deviceConsumptionWater.map { it.statConsumption }, data.waterUnit)
    val consumption = data.summed().consumption().byStart
    calculatedRows(sources).forEach { (type, value) ->
        row("", type, KWH, consumption.map { (start, used) -> StatisticValue(start, 0, change = value(used)) })
    }
    return csv.toString()
}

/** The computed consumption rows upstream adds for the sources configured, and how each reads a period's. */
private fun calculatedRows(sources: CsvSources): List<Pair<String, (Consumption) -> Double>> {
    val grid = sources.gridIn.isNotEmpty()
    val solar = sources.solar.isNotEmpty()
    val battery = sources.batteryIn.isNotEmpty()
    val gridReturn = sources.gridOut.isNotEmpty()
    return buildList {
        if (grid) add("calculated_consumed_grid" to Consumption::usedGrid)
        if (grid && battery) add("calculated_grid_to_battery" to Consumption::gridToBattery)
        if (gridReturn && battery) add("calculated_battery_to_grid" to Consumption::batteryToGrid)
        if (battery) add("calculated_consumed_battery" to Consumption::usedBattery)
        if (solar) add("calculated_consumed_solar" to Consumption::usedSolar)
        if (solar && battery) add("calculated_solar_to_battery" to Consumption::solarToBattery)
        if (solar && gridReturn) add("calculated_solar_to_grid" to Consumption::solarToGrid)
        if (listOf(grid, solar, battery).count { it } > 1) add("calculated_total_consumption" to Consumption::usedTotal)
    }
}

/** The statistics of each kind of source, and of their costs, in the preferences' order. */
private class CsvSources(data: EnergyData) {
    val gridIn = mutableListOf<String>()
    val gridInCost = mutableListOf<String>()
    val gridOut = mutableListOf<String>()
    val gridOutCost = mutableListOf<String>()
    val batteryIn = mutableListOf<String>()
    val batteryOut = mutableListOf<String>()
    val solar = mutableListOf<String>()
    val gas = mutableListOf<String>()
    val gasCost = mutableListOf<String>()
    val water = mutableListOf<String>()
    val waterCost = mutableListOf<String>()

    init {
        val costSensors = data.info.costSensors
        data.prefs.energySources.forEach { source ->
            when (source) {
                is EnergySource.Grid -> {
                    source.statEnergyFrom?.let { from ->
                        gridIn += from
                        (source.statCost ?: costSensors[from])?.let { gridInCost += it }
                    }
                    source.statEnergyTo?.let { to ->
                        gridOut += to
                        (source.statCompensation ?: costSensors[to])?.let { gridOutCost += it }
                    }
                }
                is EnergySource.Battery -> {
                    batteryIn += source.statEnergyTo
                    batteryOut += source.statEnergyFrom
                }
                is EnergySource.Solar -> solar += source.statEnergyFrom
                is EnergySource.Utility -> {
                    val (ids, costs) = if (source.type == UtilityType.GAS) gas to gasCost else water to waterCost
                    ids += source.statEnergyFrom
                    (source.statCost ?: costSensors[source.statEnergyFrom])?.let { costs += it }
                }
            }
        }
    }
}

/** JavaScript's `String(number)`: no trailing ".0", no exponent between 1e-7 and 1e21. */
internal fun jsString(value: Double): String = when {
    value == 0.0 -> "0"
    value.isNaN() || value.isInfinite() -> value.toString()
    abs(value) >= JS_EXPONENT_ABOVE || abs(value) < JS_EXPONENT_BELOW -> value.toString()
    else -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}

private const val KWH = "kWh"
private const val JS_EXPONENT_ABOVE = 1e21
private const val JS_EXPONENT_BELOW = 1e-6
