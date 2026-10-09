package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The energy panel's gas view: the comparison, the gas graph and the gas sources.
 *
 * Port of `GasViewStrategy.generate` (frontend@20260624.6 src/panels/energy/strategies/gas-view-strategy.ts). The
 * strategy config is `{type: "gas", collection_key, hidden_cards}`.
 */
fun HassSnapshot.gasView(strategy: JsonObject, preferences: EnergyPreferences?): JsonObject {
    val options = energyViewOptions(strategy)
    val prefs = preferences?.takeIf { it.hasGasSource }
    return utilityView(options, prefs?.let { utilityCards(it, options, EnergyViewPath.GAS, GAS) }.orEmpty())
}

/**
 * The energy panel's water view: the comparison, the water graph, the water sources and the devices.
 *
 * Port of `WaterViewStrategy.generate` (src/panels/energy/strategies/water-view-strategy.ts). The strategy config
 * is `{type: "water", collection_key, hidden_cards}`.
 */
fun HassSnapshot.waterView(strategy: JsonObject, preferences: EnergyPreferences?): JsonObject {
    val options = energyViewOptions(strategy)
    val prefs = preferences?.takeIf { it.hasWaterDevices || it.hasWaterSource }
    val cards = prefs?.let { utilityCards(it, options, EnergyViewPath.WATER, WATER) + waterSankey(it, options) }
    return utilityView(options, cards.orEmpty())
}

/** The graph and sources table of a utility, after the comparison. */
private class Utility(val graph: String, val graphTitle: String, val type: String)

private val GAS = Utility("energy-gas-graph", "energy_gas_graph_title", "gas")
private val WATER = Utility("energy-water-graph", "energy_water_graph_title", "water")

private fun HassSnapshot.utilityCards(
    prefs: EnergyPreferences,
    options: EnergyViewOptions,
    view: EnergyViewPath,
    utility: Utility,
): List<JsonObject> = buildList {
    val key = options.collectionKey
    add(energyCard("energy-compare", key) { putColumns(FULL_WIDTH) })
    if (prefs.isEnergyCardVisible(view, utility.graph, options.hiddenCards)) {
        add(energyCard(utility.graph, key, utility.graphTitle) { putColumns(GRAPH_COLUMNS) })
    }
    if (prefs.isEnergyCardVisible(view, "energy-sources-table", options.hiddenCards)) {
        add(
            energyCard("energy-sources-table", key, "energy_sources_table_title") {
                putJsonArray("types") { add(utility.type) }
                putColumns(TABLE_COLUMNS)
            },
        )
    }
}

private fun HassSnapshot.waterSankey(prefs: EnergyPreferences, options: EnergyViewOptions): List<JsonObject> {
    if (!prefs.isEnergyCardVisible(EnergyViewPath.WATER, "water-sankey", options.hiddenCards)) return emptyList()
    val group = shouldShowFloorsAndAreas(prefs.deviceConsumptionWater) { it.statConsumption }
    return listOf(
        energyCard("water-sankey", options.collectionKey, "water_sankey_title") {
            putFloorsAndAreas(group)
            putColumns(GRAPH_COLUMNS)
        },
    )
}

/** One full-width section of [cards], with the date selection. */
private fun utilityView(options: EnergyViewOptions, cards: List<JsonObject>) = buildJsonObject {
    put("type", "sections")
    put("max_columns", MAX_COLUMNS)
    putJsonArray("sections") {
        addJsonObject {
            put("type", "grid")
            put("cards", JsonArray(cards))
            put("column_span", MAX_COLUMNS)
        }
    }
    putDateSelectionFooter(options.collectionKey)
}

private const val MAX_COLUMNS = 3
private const val GRAPH_COLUMNS = 24
private const val TABLE_COLUMNS = 12
