package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.energy.EnergySource
import io.homeassistant.companion.android.dashboard.energy.UtilityType

// Port of the applicability helpers of frontend@20260624.6 src/panels/energy/strategies/energy-cards.ts: the kinds
// of sources configured, which decide the views and cards.

internal val EnergyPreferences.hasGridSource: Boolean get() = energySources.any {
    it is EnergySource.Grid && (!it.statEnergyFrom.isNullOrEmpty() || !it.statEnergyTo.isNullOrEmpty())
}

internal val EnergyPreferences.hasReturn: Boolean get() =
    energySources.any { it is EnergySource.Grid && !it.statEnergyTo.isNullOrEmpty() }

internal val EnergyPreferences.hasSolar: Boolean get() = energySources.any { it is EnergySource.Solar }

internal val EnergyPreferences.hasBattery: Boolean get() = energySources.any { it is EnergySource.Battery }

/** Any electricity source: grid, solar, or battery. */
internal val EnergyPreferences.hasEnergySource: Boolean get() =
    energySources.any { it is EnergySource.Grid || it is EnergySource.Solar || it is EnergySource.Battery }

internal val EnergyPreferences.hasGasSource: Boolean get() = energySources.any { it.isUtility(UtilityType.GAS) }

internal val EnergyPreferences.hasWaterSource: Boolean get() = energySources.any { it.isUtility(UtilityType.WATER) }

internal val EnergyPreferences.hasWaterDevices: Boolean get() = deviceConsumptionWater.isNotEmpty()

internal val EnergyPreferences.hasDeviceConsumption: Boolean get() = deviceConsumption.isNotEmpty()

internal val EnergyPreferences.hasPowerSources: Boolean get() = energySources.any {
    when (it) {
        is EnergySource.Solar, is EnergySource.Battery -> !it.statRate.isNullOrEmpty()
        is EnergySource.Grid -> !it.statRate.isNullOrEmpty() || it.powerConfig != null
        is EnergySource.Utility -> false
    }
}

internal val EnergyPreferences.hasPowerDevices: Boolean get() = deviceConsumption.any { !it.statRate.isNullOrEmpty() }

internal val EnergyPreferences.hasWaterRateDevices: Boolean get() =
    deviceConsumptionWater.any { !it.statRate.isNullOrEmpty() }

/** A water source with a live flow-rate statistic. */
internal val EnergyPreferences.hasWaterRateSource: Boolean get() =
    energySources.any { it.isUtility(UtilityType.WATER) && !it.statRate.isNullOrEmpty() }

/** A gas source with a live flow-rate statistic. */
internal val EnergyPreferences.hasGasRateSource: Boolean get() =
    energySources.any { it.isUtility(UtilityType.GAS) && !it.statRate.isNullOrEmpty() }

private fun EnergySource.isUtility(type: UtilityType) = this is EnergySource.Utility && this.type == type
