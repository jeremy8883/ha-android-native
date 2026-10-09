package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.energy.EnergySource
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The energy panel's "Now" view: badges with the current totals and battery charge, the power sources graph and
 * the live power and water flows.
 *
 * Port of `PowerViewStrategy.generate` (frontend@20260624.6 src/panels/energy/strategies/power-view-strategy.ts).
 * The strategy config is `{type: "power", collection_key, hidden_cards}`.
 */
fun HassSnapshot.powerView(strategy: JsonObject, preferences: EnergyPreferences?): JsonObject {
    val options = energyViewOptions(strategy)
    val prefs = preferences?.takeIf {
        it.hasPowerSources ||
            it.hasPowerDevices ||
            it.hasWaterRateDevices ||
            it.hasWaterRateSource ||
            it.hasGasRateSource
    }
    val badges = prefs?.let { powerBadges(it, options.collectionKey) }.orEmpty()
    return buildJsonObject {
        put("type", "sections")
        putJsonArray("sections") {
            addJsonObject {
                put("type", "grid")
                put("cards", JsonArray(prefs?.let { powerCards(it, options) }.orEmpty()))
            }
        }
        if (badges.isNotEmpty()) put("badges", JsonArray(badges))
    }
}

private fun HassSnapshot.powerCards(prefs: EnergyPreferences, options: EnergyViewOptions): List<JsonObject> {
    val key = options.collectionKey
    fun visible(cardType: String) = prefs.isEnergyCardVisible(EnergyViewPath.NOW, cardType, options.hiddenCards)
    return buildList {
        if (visible("power-sources-graph")) {
            add(energyCard("power-sources-graph", key, "power_sources_graph_title") { putColumns(FULL_WIDTH) })
        }
        if (visible("power-sankey")) {
            val group = shouldShowFloorsAndAreas(prefs.deviceConsumption) { it.statRate }
            add(
                energyCard("power-sankey", key, "power_sankey_title") {
                    putFloorsAndAreas(group)
                    putColumns(FULL_WIDTH)
                },
            )
        }
        if (visible("water-flow-sankey")) {
            val group = shouldShowFloorsAndAreas(prefs.deviceConsumptionWater) { it.statRate }
            add(
                energyCard("water-flow-sankey", key, "water_flow_sankey_title") {
                    putFloorsAndAreas(group)
                    putColumns(FULL_WIDTH)
                },
            )
        }
    }
}

private fun powerBadges(prefs: EnergyPreferences, collectionKey: String): List<JsonObject> = buildList {
    fun total(type: String) = buildJsonObject {
        put("type", type)
        put("collection_key", collectionKey)
    }
    if (prefs.hasPowerSources) add(total("power-total"))
    if (prefs.hasGasRateSource) add(total("gas-total"))
    if (prefs.hasWaterRateSource) add(total("water-total"))
    prefs.energySources.filterIsInstance<EnergySource.Battery>().mapNotNull { it.statSoc?.ifEmpty { null } }.forEach {
        add(
            buildJsonObject {
                put("type", "entity")
                put("entity", it)
            },
        )
    }
}
