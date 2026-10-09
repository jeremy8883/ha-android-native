package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.stringOrNull

// The units gas and water are shown in. Port of frontend@20260624.6 src/data/energy.ts.

/**
 * The unit gas is shown in: kWh for gas metered in energy, else the meters' volume unit when they share one, else
 * the unit system's. Port of `getEnergyGasUnit`.
 */
internal fun EnergyEnvironment.energyGasUnit(
    prefs: EnergyPreferences,
    metadata: Map<String, StatisticsMetadata>,
): String {
    val gas = prefs.energySources.filterIsInstance<EnergySource.Utility>().filter { it.type == UtilityType.GAS }
    val unitClass = gas.firstNotNullOfOrNull {
        metadata[it.statEnergyFrom]?.unitClass?.takeIf(GAS_UNIT_CLASSES::contains)
    }
    if (unitClass == ENERGY_UNIT_CLASS) return "kWh"
    return sharedVolumeUnit(gas.map { displayUnit(it.statEnergyFrom, metadata[it.statEnergyFrom]) })
        ?: if (metric) "m³" else "ft³"
}

/** The unit water is shown in: the meters' volume unit when they share one, else the unit system's. */
internal fun EnergyEnvironment.energyWaterUnit(
    prefs: EnergyPreferences,
    metadata: Map<String, StatisticsMetadata>,
): String {
    val water = prefs.energySources.filterIsInstance<EnergySource.Utility>().filter { it.type == UtilityType.WATER }
    return sharedVolumeUnit(water.map { displayUnit(it.statEnergyFrom, metadata[it.statEnergyFrom]) })
        ?: if (metric) "L" else "gal"
}

private fun sharedVolumeUnit(units: List<String?>): String? =
    units.firstOrNull()?.takeIf { first -> first in VOLUME_UNITS && units.all { it == first } }

/**
 * What planning the fetch reads from the frontend's state, apart so that the data is only fetched again when it
 * changes.
 *
 * @property metric whether the unit system is metric (lengths in km)
 * @property co2SignalEntity the first CO2 Signal entity giving the fossil fuel percentage
 * @property stateUnits the unit of the gas and water meters that are entities with one, by statistic id
 */
data class EnergyEnvironment(val metric: Boolean, val co2SignalEntity: String?, val stateUnits: Map<String, String?>) {
    /** [displayUnit] with the units known here. */
    fun displayUnit(statisticId: String, metadata: StatisticsMetadata?): String? =
        if (statisticId in stateUnits) stateUnits[statisticId] else metadata?.unitOfMeasurement
}

/** The [EnergyEnvironment] of [prefs]. */
fun HassSnapshot.energyEnvironment(prefs: EnergyPreferences): EnergyEnvironment {
    val meters = prefs.energySources.filterIsInstance<EnergySource.Utility>().map { it.statEnergyFrom }
    return EnergyEnvironment(
        metric = config.unitSystem["length"] == "km",
        co2SignalEntity = co2SignalEntity(),
        stateUnits = meters.mapNotNull { id ->
            states[id]?.attributes?.get(UNIT_OF_MEASUREMENT)?.let { id to it.stringOrNull }
        }.toMap(),
    )
}

/** The first CO2 Signal entity giving the fossil fuel percentage, as `getEnergyData` finds it. */
private fun HassSnapshot.co2SignalEntity(): String? = registries.entities.values.firstOrNull { entry ->
    entry.platform == "co2signal" &&
        states[entry.entityId]?.attributes?.get(UNIT_OF_MEASUREMENT)?.stringOrNull == "%"
}?.entityId

/** The unit of a statistic: its entity's when it has one, else the recorder's. Port of `getDisplayUnit`. */
fun HassSnapshot.displayUnit(statisticId: String, metadata: StatisticsMetadata?): String? {
    val attributes = states[statisticId]?.attributes
    val unit = attributes?.get(UNIT_OF_MEASUREMENT)
    return if (unit == null) metadata?.unitOfMeasurement else unit.stringOrNull
}

private const val ENERGY_UNIT_CLASS = "energy"
private const val UNIT_OF_MEASUREMENT = "unit_of_measurement"
private val GAS_UNIT_CLASSES = setOf("volume", ENERGY_UNIT_CLASS)
