package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.assumedState
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonNull

// Ports of `ha-state-control-cover-buttons` and `-valve-buttons` (frontend@20260624.6 src/state-control/), with
// `getCoverLayout`, `getValveButtons` and the `can*` helpers (src/data/cover.ts, valve.ts).

/** Where a button sits. */
sealed interface ButtonSlot {
    /** Open (the top of the cross). */
    data object Open : ButtonSlot

    /** Stop (the middle). */
    data object Stop : ButtonSlot

    /** Close (the bottom). */
    data object Close : ButtonSlot

    /** Open the tilt (the right). */
    data object OpenTilt : ButtonSlot

    /** Close the tilt (the left). */
    data object CloseTilt : ButtonSlot
}

/** One tall button: its [actions] are the calls a tap makes (stop may stop both the cover and its tilt). */
data class PositionButton(
    val slot: ButtonSlot,
    val label: String,
    val icon: String,
    val enabled: Boolean,
    val actions: List<CardAction.CallService>,
)

/** The buttons' layout. */
sealed interface PositionButtons {
    val buttons: List<PositionButton>

    /** A column, top to bottom. */
    data class Line(override val buttons: List<PositionButton>) : PositionButtons

    /** A cross: open above, close below, the tilt either side of stop. */
    data class Cross(override val buttons: List<PositionButton>) : PositionButtons
}

/** The buttons of a cover or valve, as upstream lays them out for what it supports. */
internal fun HassSnapshot.positionButtons(state: EntityState): PositionButtons {
    val supports = { feature: Int -> state.supportsFeature(feature) }
    val open = supports(FEATURE_OPEN)
    val close = supports(FEATURE_CLOSE)
    val stop = supports(FEATURE_STOP)
    val cover = state.domain == COVER
    val openTilt = cover && supports(FEATURE_OPEN_TILT)
    val closeTilt = cover && supports(FEATURE_CLOSE_TILT)
    val stopTilt = cover && supports(FEATURE_STOP_TILT)
    val button = { slot: ButtonSlot -> positionButton(state, slot) }
    return when {
        (open || close) && (openTilt || closeTilt) -> PositionButtons.Cross(
            listOfNotNull(
                button(ButtonSlot.Open).takeIf { open },
                button(ButtonSlot.CloseTilt).takeIf { closeTilt },
                button(ButtonSlot.Stop).takeIf { stop || stopTilt },
                button(ButtonSlot.OpenTilt).takeIf { openTilt },
                button(ButtonSlot.Close).takeIf { close },
            ),
        )
        open || close -> PositionButtons.Line(
            listOfNotNull(
                button(ButtonSlot.Open).takeIf { open },
                button(ButtonSlot.Stop).takeIf { stop },
                button(ButtonSlot.Close).takeIf { close },
            ),
        )
        else -> PositionButtons.Line(
            listOfNotNull(
                button(ButtonSlot.OpenTilt).takeIf { openTilt },
                button(ButtonSlot.Stop).takeIf { stopTilt },
                button(ButtonSlot.CloseTilt).takeIf { closeTilt },
            ),
        )
    }
}

private fun HassSnapshot.positionButton(state: EntityState, slot: ButtonSlot): PositionButton {
    val domain = state.domain
    val noun = if (domain == COVER) "cover" else "valve"
    val call = { service: String -> CardAction.CallService(domain, service, entityData(state), target = null) }
    val strings = "ui.card.$domain"
    return when (slot) {
        ButtonSlot.Open -> PositionButton(
            slot,
            localize("$strings.open_$noun"),
            moveIcon(state, opening = true),
            canMove(state, opening = true),
            listOf(call("open_$noun")),
        )
        ButtonSlot.Close -> PositionButton(
            slot,
            localize("$strings.close_$noun"),
            moveIcon(state, opening = false),
            canMove(state, opening = false),
            listOf(call("close_$noun")),
        )
        // Upstream's stop calls each stop the cover supports
        ButtonSlot.Stop -> PositionButton(
            slot,
            localize("$strings.stop_$noun"),
            "mdi:stop",
            state.available(),
            stopCalls(state),
        )
        ButtonSlot.OpenTilt -> PositionButton(
            slot,
            localize("$strings.open_tilt_cover"),
            "mdi:arrow-top-right",
            canMoveTilt(state, opening = true),
            listOf(call("open_cover_tilt")),
        )
        ButtonSlot.CloseTilt -> PositionButton(
            slot,
            localize("$strings.close_tilt_cover"),
            "mdi:arrow-bottom-left",
            canMoveTilt(state, opening = false),
            listOf(call("close_cover_tilt")),
        )
    }
}

/** A valve's own icons, else `computeOpenIcon`/`computeCloseIcon`: sideways for awnings, doors, gates and curtains. */
private fun moveIcon(state: EntityState, opening: Boolean): String {
    val horizontal = state.attributes.string("device_class") in HORIZONTAL_CLASSES
    return when {
        state.domain == VALVE -> if (opening) "mdi:valve-open" else "mdi:valve-closed"
        horizontal -> if (opening) "mdi:arrow-expand-horizontal" else "mdi:arrow-collapse-horizontal"
        else -> if (opening) "mdi:arrow-up" else "mdi:arrow-down"
    }
}

/** A valve's stop, or each stop a cover supports. */
private fun stopCalls(state: EntityState): List<CardAction.CallService> {
    val call = { service: String -> CardAction.CallService(state.domain, service, entityData(state), target = null) }
    return if (state.domain == VALVE) {
        listOf(call("stop_valve"))
    } else {
        listOfNotNull(
            call("stop_cover").takeIf { state.supportsFeature(FEATURE_STOP) },
            call("stop_cover_tilt").takeIf { state.supportsFeature(FEATURE_STOP_TILT) },
        )
    }
}

/**
 * Ports of `canOpen` and `canClose`: available, and not already there or on its way, unless only assumed. A cover
 * with a position (even null) is judged by it, a valve only by a known one.
 */
private fun canMove(state: EntityState, opening: Boolean): Boolean {
    val raw = state.attributes[CURRENT_POSITION]
    val byPosition = if (state.domain == COVER) raw != null else raw != null && raw !is JsonNull
    val there = if (byPosition) {
        state.attributes.numberOrNull(CURRENT_POSITION) == if (opening) FULL else 0.0
    } else {
        state.state == if (opening) "open" else "closed"
    }
    val moving = state.state == if (opening) "opening" else "closing"
    return state.available() && (state.assumedState() || (!there && !moving))
}

/** Ports of `canOpenTilt` and `canCloseTilt`: available, and not at the end already, unless only assumed. */
private fun canMoveTilt(state: EntityState, opening: Boolean): Boolean = state.available() &&
    (state.assumedState() || state.attributes.numberOrNull(CURRENT_TILT_POSITION) != if (opening) FULL else 0.0)

/** `computeOpenIcon`'s device classes that open sideways. */
private val HORIZONTAL_CLASSES = setOf("awning", "door", "gate", "curtain")
