package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string

/**
 * The small badge on a tile's icon (`ha-tile-badge`): an [icon] (`null` when the attribute has none, which leaves
 * the badge empty) in white on [color].
 */
data class TileBadge(val icon: String?, val color: DisplayColor?)

/**
 * Port of `renderTileBadge` (frontend@20260624.6 src/panels/lovelace/cards/tile/badges/tile-badge.ts): a warning
 * while unavailable, the zone of a person or device tracker, and what a thermostat or humidifier is doing; none
 * otherwise, nor while the state is unknown.
 */
fun HassSnapshot.tileBadge(state: EntityState): TileBadge? = when {
    state.state == STATE_UNKNOWN -> null
    state.state == STATE_UNAVAILABLE -> TileBadge("mdi:exclamation-thick", DisplayColor.Theme("orange"))
    state.domain == "person" || state.domain == "device_tracker" -> personBadge(state)
    state.domain == "climate" -> actionBadge(state, "hvac_action", CLIMATE_HVAC_ACTION_TO_MODE)
    state.domain == "humidifier" -> actionBadge(state, "action", HUMIDIFIER_ACTION_MODE)
    else -> null
}

/**
 * Port of `renderPersonBadge`: the icon of the zone the person is in (by its name), else home or away, in the
 * state's colour.
 */
private fun HassSnapshot.personBadge(state: EntityState): TileBadge {
    val zoneIcon = if (state.state == "home" || state.state == "not_home") {
        null
    } else {
        states.values.firstOrNull { it.domain == "zone" && it.attributes.string("friendly_name") == state.state }
            ?.attributes?.string("icon")?.ifEmpty { null }
    }
    val icon = zoneIcon ?: if (state.state == "not_home") "mdi:home-export-outline" else "mdi:home"
    return TileBadge(icon, stateColor(state))
}

/**
 * Ports of `renderClimateBadge` and `renderHumidifierBadge`: the [attribute] action's icon, in the colour of the
 * mode it matches; none while off or without an action.
 */
private fun HassSnapshot.actionBadge(state: EntityState, attribute: String, modes: Map<String, String>): TileBadge? {
    val action = state.attributes.string(attribute)?.takeIf { it.isNotEmpty() && it != "off" } ?: return null
    return TileBadge(attributeIcon(state, attribute, action), stateColor(state, modes[action]))
}

/** `CLIMATE_HVAC_ACTION_TO_MODE` (src/data/climate.ts). */
private val CLIMATE_HVAC_ACTION_TO_MODE = mapOf(
    "cooling" to "cool",
    "defrosting" to "heat",
    "drying" to "dry",
    "fan" to "fan_only",
    "heating" to "heat",
    "idle" to "off",
    "off" to "off",
    "preheating" to "heat",
)

/** `HUMIDIFIER_ACTION_MODE` (src/data/humidifier.ts). */
private val HUMIDIFIER_ACTION_MODE = mapOf("drying" to "on", "humidifying" to "on", "idle" to "off", "off" to "off")
