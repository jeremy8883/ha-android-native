package io.homeassistant.companion.android.dashboard.energy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Everything the energy cards show for a period. Port of `EnergyData` (frontend@20260624.6 src/data/energy.ts).
 *
 * @property stats the statistics of the period, power and flow rates included
 * @property statsCompare the statistics of [comparePeriod], `null` without comparison
 * @property fossilEnergyConsumption the fossil fuel share of the grid's energy by period start, with a CO2 signal
 */
data class EnergyData(
    val period: EnergyPeriod,
    val comparePeriod: EnergyPeriod?,
    val compareMode: CompareMode?,
    val prefs: EnergyPreferences,
    val info: EnergyInfo,
    val stats: Statistics,
    val statsMetadata: Map<String, StatisticsMetadata>,
    val statsCompare: Statistics?,
    val co2SignalEntity: String?,
    val fossilEnergyConsumption: Map<String, Double>?,
    val fossilEnergyConsumptionCompare: Map<String, Double>?,
    val waterUnit: String,
    val gasUnit: String,
)

/** The results of the commands of an [EnergyFetchPlan], each `null` when its command wasn't sent. */
data class EnergyFetchResults(
    val energy: JsonObject? = null,
    val power: JsonObject? = null,
    val powerHour: JsonObject? = null,
    val water: JsonObject? = null,
    val energyCompare: JsonObject? = null,
    val waterCompare: JsonObject? = null,
    val fossil: JsonObject? = null,
    val fossilCompare: JsonObject? = null,
)

/** The energy data from the plan's [results], as `getEnergyData` puts them together. */
fun EnergyFetchPlan.assemble(
    prefs: EnergyPreferences,
    info: EnergyInfo,
    metadata: List<StatisticsMetadata>,
    results: EnergyFetchResults,
): EnergyData {
    val power = backFillPower(
        fine = results.power?.let(::parseStatistics).orEmpty(),
        hourly = results.powerHour?.let(::parseStatistics).orEmpty(),
    )
    val stats = results.energy?.let(::parseStatistics).orEmpty() +
        results.water?.let(::parseStatistics).orEmpty() +
        power
    val statsCompare = comparePeriod?.let {
        results.energyCompare?.let(::parseStatistics).orEmpty() + results.waterCompare?.let(::parseStatistics).orEmpty()
    }
    return EnergyData(
        period = period,
        comparePeriod = comparePeriod,
        compareMode = compareMode,
        prefs = prefs,
        info = info,
        stats = stats,
        statsMetadata = metadata.associateBy { it.statisticId },
        statsCompare = statsCompare,
        co2SignalEntity = co2SignalEntity,
        fossilEnergyConsumption = results.fossil?.let(::parseFossil),
        fossilEnergyConsumptionCompare = results.fossilCompare?.let(::parseFossil),
        waterUnit = waterUnit,
        gasUnit = gasUnit,
    )
}

/**
 * The fine power statistics, preceded by the hourly ones from before the first fine one: 5-minute statistics are
 * purged after some days, so a period reaching further back is filled in by hour.
 */
private fun backFillPower(fine: Statistics, hourly: Statistics): Statistics {
    if (hourly.isEmpty()) return fine
    return fine + hourly.mapValues { (id, hours) ->
        val first = fine[id]?.firstOrNull() ?: return@mapValues hours
        hours.takeWhile { it.end <= first.start } + fine.getValue(id)
    }
}

private fun parseFossil(result: JsonObject): Map<String, Double> =
    result.mapNotNull { (start, value) -> (value as? JsonPrimitive)?.doubleOrNull?.let { start to it } }.toMap()
