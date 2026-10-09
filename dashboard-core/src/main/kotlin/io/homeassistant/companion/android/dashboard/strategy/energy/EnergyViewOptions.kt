package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.DeviceConsumption
import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// What the energy view strategies share

/** The options every energy view strategy reads from its config. Port of `EnergyViewStrategyConfig`. */
internal class EnergyViewOptions(val collectionKey: String, val hiddenCards: List<String>?)

internal fun energyViewOptions(strategy: JsonObject) = EnergyViewOptions(
    collectionKey = strategy.string("collection_key") ?: DEFAULT_ENERGY_COLLECTION_KEY,
    hiddenCards = strategy.array("hidden_cards")?.mapNotNull { it.stringOrNull },
)

/** The date selection the period views show at the bottom. */
internal fun JsonObjectBuilder.putDateSelectionFooter(collectionKey: String) {
    putJsonObject("footer") {
        putJsonObject("card") {
            put("type", "energy-date-selection")
            put("collection_key", collectionKey)
            put("opening_direction", "right")
            put("vertical_opening_direction", "up")
        }
    }
}

/** An energy card of [type] reading [collectionKey], titled with [titleKey] when given. */
internal fun HassSnapshot.energyCard(
    type: String,
    collectionKey: String,
    titleKey: String? = null,
    more: JsonObjectBuilder.() -> Unit = {},
): JsonObject = buildJsonObject {
    titleKey?.let { put("title", localize("ui.panel.energy.cards.$it")) }
    put("type", type)
    put("collection_key", collectionKey)
    more()
}

/** Sets the card's width in the section grid. */
internal fun JsonObjectBuilder.putColumns(columns: Int) {
    putJsonObject("grid_options") { put("columns", columns) }
}

/** Groups a sankey by floor and area, as [shouldShowFloorsAndAreas] decides. */
internal fun JsonObjectBuilder.putFloorsAndAreas(show: Boolean) {
    put("group_by_floor", show)
    put("group_by_area", show)
}

/**
 * Whether grouping [devices] by floor and area keeps each included device next to its parent: no device is in
 * another area than the device it's included in. [statisticOf] picks the statistic whose entity is located.
 *
 * Port of `shouldShowFloorsAndAreas` (frontend@20260624.6 src/panels/energy/strategies/show-floors-and-areas.ts).
 */
internal fun HassSnapshot.shouldShowFloorsAndAreas(
    devices: List<DeviceConsumption>,
    statisticOf: (DeviceConsumption) -> String?,
): Boolean {
    val byStatistic = devices.associateBy { it.statConsumption }
    return devices.none { device ->
        val parent = device.includedInStat?.let(byStatistic::get) ?: return@none false
        val childArea = statisticOf(device)?.let(::entityAreaId)
        val parentArea = statisticOf(parent)?.let(::entityAreaId)
        childArea != null && parentArea != null && childArea != parentArea
    }
}

/** Port of `getEntityAreaId`: the entity's own area, else its device's. */
private fun HassSnapshot.entityAreaId(entityId: String): String? {
    val entry = registries.entities[entityId] ?: return null
    return entry.areaId ?: entry.deviceId?.let { registries.devices[it]?.areaId }
}

/** The preferences the energy view strategies were generated from, `null` when energy is not set up. */
internal fun EnergyPreferences?.configured(): EnergyPreferences? = this?.takeUnless { it.isEmpty }

/** The `grid_options.columns` of a full-width card. */
internal const val FULL_WIDTH = 36
