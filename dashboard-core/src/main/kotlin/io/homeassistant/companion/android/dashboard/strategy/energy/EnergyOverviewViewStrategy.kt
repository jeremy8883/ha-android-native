package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The energy panel's overview: one section each for the distribution, the sources' totals, power, electricity,
 * gas and water.
 *
 * Port of `EnergyOverviewViewStrategy.generate` (frontend@20260624.6
 * src/panels/energy/strategies/energy-overview-view-strategy.ts). The strategy config is
 * `{type: "energy-overview", collection_key, hidden_cards}`.
 */
fun HassSnapshot.energyOverviewView(strategy: JsonObject, preferences: EnergyPreferences?): JsonObject {
    val options = energyViewOptions(strategy)
    val cards = preferences.configured()?.let { overviewCards(it, options) }.orEmpty()
    return buildJsonObject {
        put("type", "sections")
        putJsonArray("sections") {
            cards.forEach { card ->
                addJsonObject {
                    put("type", "grid")
                    put("cards", JsonArray(listOf(card)))
                }
            }
        }
        put("dense_section_placement", true)
        put("max_columns", MAX_COLUMNS)
        putDateSelectionFooter(options.collectionKey)
    }
}

private fun HassSnapshot.overviewCards(prefs: EnergyPreferences, options: EnergyViewOptions): List<JsonObject> {
    val key = options.collectionKey
    fun visible(cardType: String) = prefs.isEnergyCardVisible(EnergyViewPath.OVERVIEW, cardType, options.hiddenCards)
    return buildList {
        if (visible("energy-distribution")) add(energyCard("energy-distribution", key, "energy_distribution_title"))
        if (visible("energy-sources-table")) {
            add(energyCard("energy-sources-table", key, "energy_sources_table_title") { put("show_only_totals", true) })
        }
        if (visible("power-sources-graph")) {
            add(energyCard("power-sources-graph", key, "power_sources_graph_title") { put("show_legend", false) })
        }
        if (visible("energy-usage-graph")) add(energyCard("energy-usage-graph", key, "energy_usage_graph_title"))
        if (visible("energy-gas-graph")) add(energyCard("energy-gas-graph", key, "energy_gas_graph_title"))
        if (visible("energy-water-graph")) {
            add(
                if (prefs.hasWaterSource) {
                    energyCard("energy-water-graph", key, "energy_water_graph_title")
                } else {
                    energyCard("water-sankey", key, "water_sankey_title")
                },
            )
        }
    }
}

private const val MAX_COLUMNS = 3
