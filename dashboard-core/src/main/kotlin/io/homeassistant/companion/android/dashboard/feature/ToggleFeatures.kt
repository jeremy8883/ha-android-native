package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the on/off card features (frontend@20260624.6 src/panels/lovelace/card-features/): the toggle, the
// humidifier toggle and the fan's oscillation.

/**
 * Port of `hui-toggle-card-feature`: a switch in the feature colour, or off and on buttons when the state is only
 * assumed or unknown, the one matching the state filled.
 */
internal fun HassSnapshot.toggleFeature(state: EntityState): TileFeature? {
    if (state.domain !in TOGGLE_DOMAINS) return null
    val (onIcon, offIcon) = TOGGLE_ICONS[state.domain] ?: ("mdi:power" to "mdi:power-off")
    fun call(service: String) = CardAction.CallService(state.domain, service, entityData(state), target = null)
    return if (state.assumedState() || state.state == UNKNOWN) {
        TileFeature.Buttons(
            listOf(
                TileFeature.Button(
                    localize("ui.card.common.turn_off"),
                    offIcon,
                    state.available(),
                    call(TURN_OFF),
                    active = state.state == OFF,
                    color = stateColor(state, OFF),
                ),
                // The on colour is the feature colour
                TileFeature.Button(
                    localize("ui.card.common.turn_on"),
                    onIcon,
                    state.available(),
                    call(TURN_ON),
                    active = state.state == ON,
                ),
            ),
        )
    } else {
        TileFeature.Switch(
            label = localize("ui.card.common.toggle"),
            checked = state.state == ON,
            enabled = state.available(),
            onIcon = onIcon,
            offIcon = offIcon,
            turnOn = call(TURN_ON),
            turnOff = call(TURN_OFF),
        )
    }
}

/** Port of `hui-humidifier-toggle-card-feature`: off and on as icons, in the state's colour. */
internal fun HassSnapshot.humidifierToggle(state: EntityState): TileFeature? {
    if (state.domain != "humidifier") return null
    return TileFeature.Select(
        label = localize("ui.card.humidifier.state"),
        options = listOf(OFF to "mdi:power", ON to "mdi:water-percent").map { (value, icon) ->
            TileFeature.Option(
                value = value,
                label = formatEntityState(state, value),
                icon = icon,
                action = CardAction.CallService(
                    "humidifier",
                    if (value == ON) TURN_ON else TURN_OFF,
                    entityData(state),
                    target = null,
                ),
            )
        },
        selected = state.state,
        enabled = state.available(),
        color = stateColor(state),
    )
}

/** Port of `hui-fan-oscillate-card-feature`: no and yes as icons, in the state's colour. */
internal fun HassSnapshot.fanOscillate(state: EntityState): TileFeature? {
    if (state.domain != "fan" || !state.supportsFeature(FAN_OSCILLATE)) return null
    val oscillating = state.attributes.boolean("oscillating") == true
    return TileFeature.Select(
        label = localize("ui.card.fan.oscillate"),
        options = listOf(false, true).map { on ->
            TileFeature.Option(
                value = if (on) YES else NO,
                label = localize("ui.common.${if (on) YES else NO}"),
                icon = if (on) "mdi:arrow-oscillating" else "mdi:arrow-oscillating-off",
                action = CardAction.CallService(
                    "fan",
                    "oscillate",
                    JsonObject(entityData(state) + ("oscillating" to JsonPrimitive(on))),
                    target = null,
                ),
            )
        },
        selected = if (oscillating) YES else NO,
        enabled = state.available(),
        color = stateColor(state),
    )
}

private const val ON = "on"
private const val OFF = "off"
private const val UNKNOWN = "unknown"
private const val YES = "yes"
private const val NO = "no"
private const val TURN_ON = "turn_on"
private const val TURN_OFF = "turn_off"
private const val FAN_OSCILLATE = 2
private val TOGGLE_DOMAINS = setOf("switch", "input_boolean", "light", "fan", "siren", "automation")

/** `DOMAIN_ICONS`: the on and off icons of the domains that have their own. */
private val TOGGLE_ICONS = mapOf(
    "siren" to ("mdi:volume-high" to "mdi:volume-off"),
    "light" to ("mdi:lightbulb-on" to "mdi:lightbulb-off"),
    "fan" to ("mdi:fan" to "mdi:fan-off"),
)
