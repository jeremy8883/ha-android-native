package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.SENSOR_TIMESTAMP_DEVICE_CLASSES
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.timerDisplay
import io.homeassistant.companion.android.dashboard.display.timestampDisplay
import io.homeassistant.companion.android.dashboard.display.updateStateDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonObject

// Port of `state-card-content` and the cards it picks (frontend@20260624.6 src/state-summary/,
// src/common/entity/state_card_type.ts, can_toggle_state.ts and the state components they use:
// ha-climate-state, ha-humidifier-state, ha-water_heater-state, ha-vacuum-state, ha-lawn_mower-action-button,
// ha-cover-controls, ha-cover-tilt-controls, ha-entity-toggle, hass-media-player-model).

/**
 * What a state card shows right of the entity's name: its [state] (and a [secondary] line under it), or a
 * [control] in its place.
 */
data class StateCardRight(val state: String?, val secondary: String? = null, val control: RowControl? = null)

/** The right side of [state]'s state card, as `stateCardType` picks it. */
fun HassSnapshot.stateCardRight(state: EntityState, now: Instant): StateCardRight = when {
    state.state == STATE_UNAVAILABLE -> displayRight(state, now)
    state.domain in DOMAINS_WITH_CARD -> domainRight(state, now)
    canToggleState(
        state,
    ) &&
        state.attributes.string("control") != "hidden" -> StateCardRight(null, control = entityToggle(state))
    else -> displayRight(state, now)
}

private fun HassSnapshot.domainRight(state: EntityState, now: Instant): StateCardRight = when (state.domain) {
    "climate" -> climateRight(state)
    "event" -> StateCardRight(formatEntityState(state), formatEntityAttributeValue(state, "event_type"))
    "humidifier" -> humidifierRight(state)
    "media_player" -> mediaRight(state)
    "timer" -> StateCardRight(timerDisplay(state, now), control = timerRowControl(state))
    "update" -> textRight(updateStateDisplay(state))
    "water_heater" -> waterHeaterRight(state)
    // An inactive alert shows its state
    "alert" -> if (state.isActive()) {
        StateCardRight(
            null,
            control = entityToggle(state),
        )
    } else {
        textRight(formatEntityState(state))
    }
    else -> domainControl(state)?.let { StateCardRight(null, control = it) } ?: displayRight(state, now)
}

/** The control of the cards with one: buttons, a cover's controls, or an input (`null` for a configurator). */
private fun HassSnapshot.domainControl(state: EntityState): RowControl? = when (state.domain) {
    "button", "input_button", "scene" -> rowControl(state, JsonObject(emptyMap()))
    "cover" -> coverControls(state)
    "lawn_mower" -> mowerButton(state)
    "lock" -> lockButtons(state)
    "script" -> scriptButtons(state, null) { domain, service -> serviceCall(state, domain, service) }
    "vacuum" -> vacuumButton(state)
    // An input text helper's card names it beside the field (`state-card-input_text`), a text entity's in it
    "input_text" -> (inputRowControl(state, "") as? RowTextInput)?.copy(label = "")
    // input_number, number, input_select, select, text
    else -> inputRowControl(state, entityName(state) ?: state.entityId)
}

internal fun textRight(value: String) = StateCardRight(value)

internal fun serviceCall(state: EntityState, domain: String, service: String) =
    CardAction.CallService(domain, service, entityData(state), target = null)

/** `state-card-display`: the state, a timestamp sensor's as a date and time (or for how long, for uptime). */
private fun HassSnapshot.displayRight(state: EntityState, now: Instant): StateCardRight {
    val deviceClass = state.attributes.string("device_class")
    val timestamp = state.domain == "sensor" &&
        deviceClass in SENSOR_TIMESTAMP_DEVICE_CLASSES &&
        state.state != STATE_UNAVAILABLE &&
        state.state != STATE_UNKNOWN
    return textRight(
        if (timestamp) {
            timestampDisplay(state.state, now, if (deviceClass == "uptime") "total" else "datetime")
        } else {
            formatEntityState(state)
        },
    )
}

/** Port of `DOMAINS_WITH_CARD`. */
private val DOMAINS_WITH_CARD = setOf(
    "alert", "button", "climate", "cover", "configurator", "event", "input_button", "input_select", "input_number",
    "input_text", "humidifier", "lawn_mower", "lock", "media_player", "number", "scene", "script", "select", "timer",
    "text", "update", "vacuum", "water_heater",
)
