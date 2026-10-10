package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.ButtonSlot
import io.homeassistant.companion.android.dashboard.moreinfo.positionButton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

// The controls of the state cards: ha-entity-toggle and `canToggleState`, ha-cover-controls, state-card-lock,
// ha-vacuum-state and ha-lawn_mower-action-button (frontend@20260624.6 src/components/, src/state-summary/,
// src/common/entity/can_toggle_state.ts).

/**
 * Port of `canToggleState`: a group with a member that can toggle (while on or off), a climate entity that can
 * turn on, else one whose domain has the action (`turn_on`, a lock's `lock`, a cover's `open_cover`).
 */
internal fun HassSnapshot.canToggleState(state: EntityState): Boolean = when (state.domain) {
    "group" -> (state.attributes["entity_id"] as? JsonArray).orEmpty().any { member ->
        (member as? JsonPrimitive)?.content?.let(states::get)?.let { canToggleDomain(it.domain) } == true
    } &&
        (state.state == "on" || state.state == "off")
    "climate" -> state.supportsFeature(CLIMATE_TURN_ON)
    else -> canToggleDomain(state.domain)
}

private fun HassSnapshot.canToggleDomain(domain: String): Boolean {
    val actions = services?.get(domain) ?: return false
    return when (domain) {
        "lock" -> "lock" in actions
        "cover" -> "open_cover" in actions
        else -> "turn_on" in actions
    }
}

/**
 * Port of `ha-entity-toggle`: a switch, or off and on buttons when the state is only assumed or unknown (the
 * current one marked).
 */
internal fun HassSnapshot.entityToggle(state: EntityState): RowControl {
    val on = state.state !in STATES_OFF && state.state != STATE_UNAVAILABLE && state.state != STATE_UNKNOWN
    val available = state.state != STATE_UNAVAILABLE
    if (jsTruthy(state.attributes["assumed_state"]) || state.state == STATE_UNKNOWN) {
        // `computeStateName`
        val name = state.attributes.string("friendly_name") ?: state.entityId.substringAfter('.').replace('_', ' ')
        return RowControl.IconButtons(
            listOf(
                RowIconButton(
                    "mdi:flash-off",
                    "Turn $name off",
                    available,
                    true,
                    !on && state.state != STATE_UNKNOWN,
                    turnOnOff(state, false),
                ),
                RowIconButton("mdi:flash", "Turn $name on", available, true, on, turnOnOff(state, true)),
            ),
        )
    }
    return RowControl.Toggle(on, available, turnOnOff(state, !on), state.lastUpdated)
}

/** `ha-entity-toggle`'s call: unlock/lock, open/close, or `homeassistant` on/off for a group. */
private fun turnOnOff(state: EntityState, turnOn: Boolean): CardAction.CallService {
    val (domain, service) = when (state.domain) {
        "lock" -> "lock" to if (turnOn) "unlock" else "lock"
        "cover" -> "cover" to if (turnOn) "open_cover" else "close_cover"
        "valve" -> "valve" to if (turnOn) "open_valve" else "close_valve"
        "group" -> "homeassistant" to if (turnOn) "turn_on" else "turn_off"
        else -> state.domain to if (turnOn) "turn_on" else "turn_off"
    }
    return CardAction.CallService(domain, service, entityData(state), target = null)
}

/** Port of `ha-cover-controls` (open, stop, close; unsupported ones keep their place) or of the tilt controls. */
internal fun HassSnapshot.coverControls(state: EntityState): RowControl {
    // `isTiltOnly`
    val tiltOnly = listOf(COVER_OPEN_TILT, COVER_CLOSE_TILT, COVER_STOP_TILT).any(state::supportsFeature) &&
        listOf(COVER_OPEN, COVER_CLOSE, COVER_STOP).none(state::supportsFeature)
    val slots = if (tiltOnly) {
        listOf(
            ButtonSlot.OpenTilt to COVER_OPEN_TILT,
            ButtonSlot.Stop to COVER_STOP_TILT,
            ButtonSlot.CloseTilt to COVER_CLOSE_TILT,
        )
    } else {
        listOf(ButtonSlot.Open to COVER_OPEN, ButtonSlot.Stop to COVER_STOP, ButtonSlot.Close to COVER_CLOSE)
    }
    return RowControl.IconButtons(
        slots.map { (slot, feature) ->
            val button = positionButton(state, slot)
            val stopService = if (tiltOnly) "stop_cover_tilt" else "stop_cover"
            RowIconButton(
                icon = button.icon,
                label = if (slot == ButtonSlot.Stop && tiltOnly) localize("ui.card.cover.stop_cover") else button.label,
                enabled = button.enabled,
                shown = state.supportsFeature(feature),
                active = false,
                action = if (slot ==
                    ButtonSlot.Stop
                ) {
                    serviceCall(state, "cover", stopService)
                } else {
                    button.actions.first()
                },
            )
        },
    )
}

/**
 * Port of `state-card-lock`: open (upstream shows it when the lock does *not* support opening), then unlock when
 * locked or lock otherwise, asking for the code when the lock has a format.
 */
internal fun HassSnapshot.lockButtons(state: EntityState): RowControl.Buttons {
    val code = state.attributes.string("code_format")?.ifEmpty { null }
    val button = { service: String ->
        RowButton(
            localize("ui.card.lock.$service"),
            enabled = true,
            danger = false,
            action = serviceCall(state, "lock", service).copy(
                code = code?.let {
                    CodeRequest(state.entityId, "lock", codeFormat = "text", title = localize("ui.card.lock.$service"))
                },
            ),
        )
    }
    return RowControl.Buttons(
        listOfNotNull(
            button("open").takeIf { !state.supportsFeature(LOCK_OPEN) },
            button(if (state.state == "locked") "unlock" else "lock"),
        ),
    )
}

/** Port of `ha-vacuum-state`: the action the state allows (start, return, resume, on, off), else the state. */
internal fun HassSnapshot.vacuumButton(state: EntityState): RowControl.Buttons {
    val action = VACUUM_ACTIONS[state.state]
    val interceptable = action != null && number(state.attributes["supported_features"]) != 0.0
    return RowControl.Buttons(
        listOf(
            RowButton(
                label = if (interceptable) {
                    localize("ui.card.vacuum.actions.${action.first}")
                } else {
                    localize("component.vacuum.entity_component._.state.${state.state}")
                },
                enabled = interceptable,
                danger = false,
                action = serviceCall(state, "vacuum", action?.second ?: "start"),
            ),
        ),
    )
}

/** Port of `ha-lawn_mower-action-button`: the action the state allows, else the state on a disabled button. */
internal fun HassSnapshot.mowerButton(state: EntityState): RowControl.Buttons {
    val action = MOWER_ACTIONS[state.state]?.takeIf { state.supportsFeature(it.third) }
    return RowControl.Buttons(
        listOf(
            RowButton(
                label = action?.let { localize("ui.card.lawn_mower.actions.${it.first}") } ?: formatEntityState(state),
                enabled = action != null,
                danger = false,
                action = serviceCall(state, "lawn_mower", action?.second ?: "start_mowing"),
            ),
        ),
    )
}

private val STATES_OFF = setOf("closed", "locked", "off")

/** `STATES_INTERCEPTABLE`: each state's action and the call it makes. */
private val VACUUM_ACTIONS = mapOf(
    "cleaning" to ("return_to_base" to "return_to_base"),
    "docked" to ("start_cleaning" to "start"),
    "idle" to ("start_cleaning" to "start"),
    "off" to ("turn_on" to "turn_on"),
    "on" to ("turn_off" to "turn_off"),
    "paused" to ("resume_cleaning" to "start"),
)

/** `LAWN_MOWER_ACTIONS`: each state's action, its call and the feature it needs. */
private val MOWER_ACTIONS = mapOf(
    "mowing" to Triple("dock", "dock", MOWER_DOCK),
    "docked" to Triple("start_mowing", "start_mowing", MOWER_START),
    "returning" to Triple("pause", "pause", MOWER_PAUSE),
    "paused" to Triple("resume_mowing", "start_mowing", MOWER_START),
)

private const val CLIMATE_TURN_ON = 4096

private const val COVER_OPEN = 1

private const val COVER_CLOSE = 2

private const val COVER_STOP = 8

private const val COVER_OPEN_TILT = 16

private const val COVER_CLOSE_TILT = 32

private const val COVER_STOP_TILT = 64

private const val LOCK_OPEN = 1

private const val MOWER_START = 1

private const val MOWER_PAUSE = 2

private const val MOWER_DOCK = 4
