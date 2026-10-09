package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.energy.UtilityType
import io.homeassistant.companion.android.dashboard.energy.flowRate
import io.homeassistant.companion.android.dashboard.energy.powerTotal
import io.homeassistant.companion.android.dashboard.energy.powerUse
import io.homeassistant.companion.android.dashboard.energy.totalFlowRate
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.DEFAULT_ENERGY_COLLECTION_KEY
import kotlinx.serialization.json.JsonObject

/**
 * The power, gas or water total badge [badge] ([type]): the power the home uses or the gas or water flowing right
 * now. `null` for other types, and until its collection has loaded, as upstream renders nothing then. Ports of
 * `hui-power-total-badge`, `hui-gas-total-badge` and `hui-water-total-badge` (frontend@20260624.6
 * src/panels/lovelace/badges/energy/).
 */
internal fun HassSnapshot.energyBadge(type: String, badge: JsonObject): ViewBadgeModel? {
    val key = badge.string("collection_key") ?: DEFAULT_ENERGY_COLLECTION_KEY
    val (kind, prefs) = ENERGY_BADGES[type]?.let { kind -> energy[key]?.data?.prefs?.let { kind to it } } ?: return null
    val content = when (kind.flow) {
        null -> formats.powerTotal(powerUse(prefs))
        else -> formats.flowRate(totalFlowRate(prefs, kind.flow))
    }
    return ViewBadgeModel(
        entityId = "",
        icon = kind.icon,
        label = badge.string("title")?.ifEmpty { null } ?: localize("ui.panel.lovelace.cards.energy.${kind.title}"),
        content = content,
        color = null,
        active = false,
        missing = false,
        actions = NO_ACTIONS,
    )
}

/** An energy badge: its icon, title translation and the flow it shows (power without one). */
private class EnergyBadge(val icon: String, val title: String, val flow: UtilityType?)

private val ENERGY_BADGES = mapOf(
    "power-total" to EnergyBadge("mdi:home-lightning-bolt", "power_total_title", null),
    "gas-total" to EnergyBadge("mdi:fire", "gas_total_title", UtilityType.GAS),
    "water-total" to EnergyBadge("mdi:water", "water_total_title", UtilityType.WATER),
)

// The badges don't react to gestures
private val NO_ACTIONS = ElementActions(JsonObject(emptyMap()), tap = false, hold = false, doubleTap = false)
