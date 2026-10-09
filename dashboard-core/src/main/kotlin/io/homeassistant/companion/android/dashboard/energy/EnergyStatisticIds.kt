package io.homeassistant.companion.android.dashboard.energy

// The statistics the energy preferences refer to. Port of frontend@20260624.6 src/data/energy.ts.

/**
 * The statistics of [this] sources of the kinds [types] (`grid`, `solar`, `battery`, `gas`, `water`, `device`), with
 * their costs. Port of `getReferencedStatisticIds`.
 */
fun EnergyPreferences.referencedStatisticIds(info: EnergyInfo, types: Set<String>): List<String> = buildList {
    energySources.forEach { source ->
        when (source) {
            is EnergySource.Solar -> if (SOLAR in types) add(source.statEnergyFrom)
            is EnergySource.Battery -> if (BATTERY in types) {
                add(source.statEnergyFrom)
                add(source.statEnergyTo)
            }
            is EnergySource.Utility -> if (source.type.key in types) {
                add(source.statEnergyFrom)
                source.statCost?.let(::add)
                info.costSensors[source.statEnergyFrom]?.let(::add)
            }
            is EnergySource.Grid -> if (GRID in types) addGrid(source, info)
        }
    }
    if (DEVICE in types) addAll(deviceConsumption.map { it.statConsumption })
    if (WATER in types) addAll(deviceConsumptionWater.map { it.statConsumption })
}

private fun MutableList<String>.addGrid(source: EnergySource.Grid, info: EnergyInfo) {
    source.statEnergyFrom?.let { from ->
        add(from)
        source.statCost?.let(::add)
        info.costSensors[from]?.let(::add)
    }
    source.statEnergyTo?.let { to ->
        add(to)
        source.statCompensation?.let(::add)
        info.costSensors[to]?.let(::add)
    }
}

/** The power and flow rate statistics of the sources and devices. Port of `getReferencedStatisticIdsPower`. */
fun EnergyPreferences.referencedPowerStatisticIds(): List<String> = (
    energySources.map { it.statRate } + deviceConsumption.map { it.statRate } +
        deviceConsumptionWater.map { it.statRate }
    )
    .filterNot { it.isNullOrEmpty() }.filterNotNull()

/** The key upstream names the utility's source type by. */
val UtilityType.key: String
    get() = when (this) {
        UtilityType.GAS -> GAS
        UtilityType.WATER -> WATER
    }
