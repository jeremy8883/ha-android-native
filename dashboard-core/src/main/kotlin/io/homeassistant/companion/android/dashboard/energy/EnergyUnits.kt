package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.stringOrNull

// The units gas and water are shown in. Port of frontend@20260624.6 src/data/energy.ts.

/**
 * The unit gas is shown in: kWh for gas metered in energy, else the meters' volume unit when they share one, else
 * the unit system's. Port of `getEnergyGasUnit`.
 */
internal fun HassSnapshot.energyGasUnit(prefs: EnergyPreferences, metadata: Map<String, StatisticsMetadata>): String {
    val gas = prefs.energySources.filterIsInstance<EnergySource.Utility>().filter { it.type == UtilityType.GAS }
    val unitClass = gas.firstNotNullOfOrNull {
        metadata[it.statEnergyFrom]?.unitClass?.takeIf(GAS_UNIT_CLASSES::contains)
    }
    if (unitClass == ENERGY_UNIT_CLASS) return "kWh"
    return sharedVolumeUnit(gas.map { displayUnit(it.statEnergyFrom, metadata[it.statEnergyFrom]) })
        ?: if (isMetric) "m³" else "ft³"
}

/** The unit water is shown in: the meters' volume unit when they share one, else the unit system's. */
internal fun HassSnapshot.energyWaterUnit(prefs: EnergyPreferences, metadata: Map<String, StatisticsMetadata>): String {
    val water = prefs.energySources.filterIsInstance<EnergySource.Utility>().filter { it.type == UtilityType.WATER }
    return sharedVolumeUnit(water.map { displayUnit(it.statEnergyFrom, metadata[it.statEnergyFrom]) })
        ?: if (isMetric) "L" else "gal"
}

private fun sharedVolumeUnit(units: List<String?>): String? =
    units.firstOrNull()?.takeIf { first -> first in VOLUME_UNITS && units.all { it == first } }

private val HassSnapshot.isMetric: Boolean get() = config.unitSystem["length"] == "km"

/** The unit of a statistic: its entity's when it has one, else the recorder's. Port of `getDisplayUnit`. */
fun HassSnapshot.displayUnit(statisticId: String, metadata: StatisticsMetadata?): String? {
    val attributes = states[statisticId]?.attributes
    val unit = attributes?.get("unit_of_measurement")
    return if (unit == null) metadata?.unitOfMeasurement else unit.stringOrNull
}

private const val ENERGY_UNIT_CLASS = "energy"
private val GAS_UNIT_CLASSES = setOf("volume", ENERGY_UNIT_CLASS)
