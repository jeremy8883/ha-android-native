package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.strategy.home.LARGE_SCREEN_CONDITION
import io.homeassistant.companion.android.dashboard.strategy.home.SMALL_SCREEN_CONDITION
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The energy panel's electricity view: the distribution, grid balance and gauges in a sidebar on large screens (as
 * sections of their own on small ones), then the comparison, graphs, sources table and devices.
 *
 * Port of `EnergyViewStrategy.generate` (frontend@20260624.6 src/panels/energy/strategies/energy-view-strategy.ts).
 * The strategy config is `{type: "energy", collection_key, hidden_cards}`.
 */
fun HassSnapshot.electricityView(strategy: JsonObject, preferences: EnergyPreferences?): JsonObject {
    val options = energyViewOptions(strategy)
    val prefs = preferences.configured()
    val side = prefs?.let { sideCards(it, options) }.orEmpty()
    val main = prefs?.let { mainCards(it, options) }
    return buildJsonObject {
        put("type", "sections")
        put("sections", JsonArray(side.mapNotNull { it.smallScreenSection } + listOfNotNull(main?.let(::mainSection))))
        putJsonObject("sidebar") {
            putJsonArray("sections") {
                addJsonObject { put("cards", JsonArray(side.map { it.sidebarCard })) }
            }
            putJsonArray("visibility") { add(LARGE_SCREEN_CONDITION) }
        }
        putDateSelectionFooter(options.collectionKey)
    }
}

/** A card of the sidebar on large screens, and the section showing it on small ones. */
private class SideCard(val sidebarCard: JsonObject, val smallScreenSection: JsonObject?)

private fun HassSnapshot.sideCards(prefs: EnergyPreferences, options: EnergyViewOptions): List<SideCard> = buildList {
    val key = options.collectionKey
    if (prefs.isVisible("energy-distribution", options)) {
        val card = energyCard("energy-distribution", key, "energy_distribution_title")
        add(SideCard(card, smallScreenSection(listOf(card), cardsFirst = true)))
    }
    if (prefs.isVisible("energy-grid-balance", options)) {
        val card = energyCard("energy-grid-balance", key)
        add(SideCard(card, smallScreenSection(listOf(card), cardsFirst = false)))
    }
    val gauges = GAUGES.filter { prefs.isVisible(it, options) }.map { energyCard(it, key) }
    if (gauges.isNotEmpty()) {
        val grid = buildJsonObject {
            put("type", "grid")
            put("columns", if (gauges.size == 1) 1 else 2)
            put("cards", JsonArray(gauges))
        }
        val sectionCards = if (gauges.size == 1) gauges else gauges.map { JsonObject(it + (GRID_OPTIONS to HALF)) }
        add(SideCard(grid, smallScreenSection(sectionCards, cardsFirst = false)))
    }
}

/** A one-column section shown on small screens only; upstream writes the distribution's cards before visibility. */
private fun smallScreenSection(cards: List<JsonObject>, cardsFirst: Boolean) = buildJsonObject {
    put("type", "grid")
    put("column_span", 1)
    if (cardsFirst) put("cards", JsonArray(cards))
    putJsonArray("visibility") { add(SMALL_SCREEN_CONDITION) }
    if (!cardsFirst) put("cards", JsonArray(cards))
}

private fun HassSnapshot.mainCards(prefs: EnergyPreferences, options: EnergyViewOptions): List<JsonObject> {
    val key = options.collectionKey
    val full: JsonObjectBuilder.() -> Unit = { putColumns(FULL_WIDTH) }
    return buildList {
        add(energyCard("energy-compare", key, more = full))
        if (prefs.isVisible("energy-usage-graph", options)) {
            add(energyCard("energy-usage-graph", key, "energy_usage_graph_title", full))
        }
        if (prefs.isVisible("energy-solar-graph", options)) {
            add(energyCard("energy-solar-graph", key, "energy_solar_graph_title", full))
        }
        if (prefs.isVisible("energy-sources-table", options)) {
            add(
                energyCard("energy-sources-table", key, "energy_sources_table_title") {
                    putJsonArray("types") { ELECTRICITY_TYPES.forEach { add(it) } }
                    putColumns(FULL_WIDTH)
                },
            )
        }
        if (prefs.isVisible("energy-devices-detail-graph", options)) {
            add(energyCard("energy-devices-detail-graph", key, "energy_devices_detail_graph_title", full))
        }
        if (prefs.isVisible("energy-devices-graph", options)) {
            add(energyCard("energy-devices-graph", key, "energy_devices_graph_title", full))
        }
        if (prefs.isVisible("energy-sankey", options)) {
            val group = shouldShowFloorsAndAreas(prefs.deviceConsumption) { it.statConsumption }
            add(
                energyCard("energy-sankey", key, "energy_sankey_title") {
                    putFloorsAndAreas(group)
                    putColumns(FULL_WIDTH)
                },
            )
        }
    }
}

private fun mainSection(cards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    put("column_span", MAIN_COLUMN_SPAN)
    put("cards", JsonArray(cards))
}

private fun EnergyPreferences.isVisible(cardType: String, options: EnergyViewOptions) =
    isEnergyCardVisible(EnergyViewPath.ELECTRICITY, cardType, options.hiddenCards)

private val GAUGES = listOf(
    "energy-grid-neutrality-gauge",
    "energy-solar-consumed-gauge",
    "energy-self-sufficiency-gauge",
    "energy-carbon-consumed-gauge",
)
private val ELECTRICITY_TYPES = listOf("grid", "solar", "battery")
private const val GRID_OPTIONS = "grid_options"
private const val GAUGE_COLUMNS = 6
private val HALF = buildJsonObject { put("columns", GAUGE_COLUMNS) }
private const val MAIN_COLUMN_SPAN = 3
