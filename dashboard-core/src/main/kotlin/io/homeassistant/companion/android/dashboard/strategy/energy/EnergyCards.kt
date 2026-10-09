package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences

// Port of frontend@20260624.6 src/panels/energy/strategies/energy-cards.ts: which energy cards apply to the
// preferences, and which the user hid.

/** A card of a view, applicable for some preferences. Port of `EnergyCardCatalogEntry`. */
private class CatalogEntry(
    val view: EnergyViewPath,
    val cardType: String,
    val isApplicable: (EnergyPreferences) -> Boolean,
) {
    /** The identifier the user's hidden cards list: `<view>.<cardType>`. */
    val key: String get() = "${view.path}.$cardType"
}

private val ENERGY_CARD_CATALOG: List<CatalogEntry> = listOf(
    // Overview
    CatalogEntry(EnergyViewPath.OVERVIEW, "energy-distribution") {
        it.hasGridSource ||
            it.hasBattery ||
            it.hasSolar
    },
    CatalogEntry(EnergyViewPath.OVERVIEW, "energy-sources-table") { it.energySources.isNotEmpty() },
    CatalogEntry(EnergyViewPath.OVERVIEW, "power-sources-graph") { it.hasPowerSources },
    CatalogEntry(EnergyViewPath.OVERVIEW, "energy-usage-graph") { it.hasGridSource || it.hasBattery },
    CatalogEntry(EnergyViewPath.OVERVIEW, "energy-gas-graph") { it.hasGasSource },
    // One toggle gates the water row: energy-water-graph (sources) or, with only water devices, water-sankey
    CatalogEntry(EnergyViewPath.OVERVIEW, "energy-water-graph") { it.hasWaterSource || it.hasWaterDevices },
    // Electricity
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-distribution") {
        it.hasGridSource || it.hasBattery || it.hasSolar
    },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-grid-balance") { it.hasGridSource && it.hasReturn },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-grid-neutrality-gauge") { it.hasReturn },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-solar-consumed-gauge") { it.hasSolar && it.hasReturn },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-self-sufficiency-gauge") { it.hasSolar && it.hasGridSource },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-carbon-consumed-gauge") { it.hasGridSource },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-usage-graph") { it.hasGridSource || it.hasBattery },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-solar-graph") { it.hasSolar },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-sources-table") {
        it.hasGridSource || it.hasSolar || it.hasBattery
    },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-devices-detail-graph") { it.hasDeviceConsumption },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-devices-graph") { it.hasDeviceConsumption },
    CatalogEntry(EnergyViewPath.ELECTRICITY, "energy-sankey") { it.hasDeviceConsumption },
    // Gas
    CatalogEntry(EnergyViewPath.GAS, "energy-gas-graph") { it.hasGasSource },
    CatalogEntry(EnergyViewPath.GAS, "energy-sources-table") { it.hasGasSource },
    // Water
    CatalogEntry(EnergyViewPath.WATER, "energy-water-graph") { it.hasWaterSource },
    CatalogEntry(EnergyViewPath.WATER, "energy-sources-table") { it.hasWaterSource },
    CatalogEntry(EnergyViewPath.WATER, "water-sankey") { it.hasWaterDevices },
    // Now (power)
    CatalogEntry(EnergyViewPath.NOW, "power-sources-graph") { it.hasPowerSources },
    CatalogEntry(EnergyViewPath.NOW, "power-sankey") { it.hasPowerDevices },
    CatalogEntry(EnergyViewPath.NOW, "water-flow-sankey") { it.hasWaterRateDevices },
)

/**
 * Whether a view strategy shows [cardType]: the card is in the catalog, applies to these preferences, and the user
 * didn't hide it. Port of `isEnergyCardVisible`.
 */
internal fun EnergyPreferences.isEnergyCardVisible(
    view: EnergyViewPath,
    cardType: String,
    hidden: List<String>?,
): Boolean {
    val entry = ENERGY_CARD_CATALOG.firstOrNull { it.view == view && it.cardType == cardType } ?: return false
    return entry.isApplicable(this) && hidden?.contains(entry.key) != true
}

/** Whether a view has applicable cards but the user hid all of them. Port of `isEnergyViewEmpty`. */
internal fun EnergyPreferences.isEnergyViewEmpty(view: EnergyViewPath, hidden: List<String>?): Boolean {
    val applicable = ENERGY_CARD_CATALOG.filter { it.view == view && it.isApplicable(this) }.map { it.key }
    return applicable.isNotEmpty() && applicable.all { hidden?.contains(it) == true }
}
