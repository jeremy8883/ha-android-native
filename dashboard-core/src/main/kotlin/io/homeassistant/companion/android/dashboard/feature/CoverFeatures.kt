package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonPrimitive

internal fun HassSnapshot.coverOpenClose(state: EntityState): TileFeature? {
    val supportsOpen = state.supportsFeature(EntityFeature.COVER_OPEN)
    val supportsClose = state.supportsFeature(EntityFeature.COVER_CLOSE)
    if (state.domain != "cover" || (!supportsOpen && !supportsClose)) return null
    fun call(service: String) = CardAction.CallService("cover", service, entityData(state), target = null)
    val horizontal = state.attributes.string("device_class") in HORIZONTAL_COVER_CLASSES
    return TileFeature.Buttons(
        listOfNotNull(
            TileFeature.Button(
                label = localize("ui.card.cover.open_cover"),
                icon = if (horizontal) "mdi:arrow-expand-horizontal" else "mdi:arrow-up",
                enabled = canOpenCover(state),
                action = call("open_cover"),
            ).takeIf { supportsOpen },
            TileFeature.Button(
                label = localize("ui.card.cover.stop_cover"),
                icon = "mdi:stop",
                enabled = state.available(),
                action = call("stop_cover"),
            ).takeIf { state.supportsFeature(EntityFeature.COVER_STOP) },
            TileFeature.Button(
                label = localize("ui.card.cover.close_cover"),
                icon = if (horizontal) "mdi:arrow-collapse-horizontal" else "mdi:arrow-down",
                enabled = canCloseCover(state),
                action = call("close_cover"),
            ).takeIf { supportsClose },
        ),
    )
}

/** Ports of `canOpen` and `canClose` (src/data/cover.ts). */
private fun canOpenCover(state: EntityState): Boolean = state.available() &&
    (state.assumedState() || (!coverAt(state, FULLY_OPEN, "open") && state.state != "opening"))

private fun canCloseCover(state: EntityState): Boolean = state.available() &&
    (state.assumedState() || (!coverAt(state, FULLY_CLOSED, "closed") && state.state != "closing"))

/** Ports of `isFullyOpen`/`isFullyClosed`: the position when known, else the state. */
private fun coverAt(state: EntityState, position: Double, stateValue: String): Boolean {
    val current = state.attributes["current_position"] ?: return state.state == stateValue
    return (current as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull() == position
}

internal fun EntityState.assumedState() = (attributes["assumed_state"] as? JsonPrimitive)?.content == "true"

private const val FULLY_OPEN = 100.0
private const val FULLY_CLOSED = 0.0
private val HORIZONTAL_COVER_CLASSES = setOf("awning", "door", "gate", "curtain")
