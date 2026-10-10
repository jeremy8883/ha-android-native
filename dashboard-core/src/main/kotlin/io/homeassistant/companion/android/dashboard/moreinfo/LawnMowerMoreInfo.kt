package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData

// Port of `more-info-lawn_mower` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-lawn_mower.ts) with
// its helpers (src/data/lawn_mower.ts).

/**
 * What a lawn mower's details show: the battery beside the header's time, the mower drawn as it is ([visual], in
 * [color]), and the start/pause and dock buttons.
 */
data class LawnMowerMoreInfo(
    val battery: DeviceBattery?,
    val color: DisplayColor?,
    val visual: MowerVisual,
    val buttons: List<CommandButton>,
)

/** What the drawn mower does (`computeVisualState`). */
sealed interface MowerVisual {
    /** Mowing its stripes. */
    data object Mowing : MowerVisual

    /** Heading for its dock. */
    data object Returning : MowerVisual

    /** Still. */
    data object Paused : MowerVisual

    /** On its dock, charging. */
    data object Docked : MowerVisual

    /** Glowing a warning. */
    data object Error : MowerVisual

    /** Faded. */
    data object Idle : MowerVisual
}

/** The details of a lawn mower, or `null` for another entity. */
fun HassSnapshot.lawnMowerMoreInfo(state: EntityState): LawnMowerMoreInfo? {
    if (state.domain != LAWN_MOWER) return null
    val available = state.state != UNAVAILABLE
    val mowing = state.state == MOWING
    val pause = mowing && state.supportsFeature(FEATURE_PAUSE)
    val call = { service: String -> CardAction.CallService(LAWN_MOWER, service, entityData(state), target = null) }
    return LawnMowerMoreInfo(
        battery = deviceBattery(state),
        color = stateColor(state),
        visual = mowerVisual(state),
        buttons = listOfNotNull(
            CommandButton(
                label = localize("$STRINGS.${if (pause) "pause" else "start_mowing"}"),
                icon = if (pause) "mdi:pause" else "mdi:play",
                // Mowing, it can always pause; otherwise it starts unless unavailable
                enabled = available,
                action = call(if (mowing) "pause" else "start_mowing"),
            ).takeIf { state.supportsFeature(FEATURE_START_MOWING) || state.supportsFeature(FEATURE_PAUSE) },
            CommandButton(
                label = localize("$STRINGS.dock"),
                icon = "mdi:home-import-outline",
                enabled = available && state.state != "docked",
                action = call("dock"),
            ).takeIf { state.supportsFeature(FEATURE_DOCK) },
        ),
    )
}

private fun mowerVisual(state: EntityState): MowerVisual = when (state.state) {
    "error" -> MowerVisual.Error
    MOWING -> MowerVisual.Mowing
    "returning" -> MowerVisual.Returning
    "paused" -> MowerVisual.Paused
    "docked" -> MowerVisual.Docked
    else -> MowerVisual.Idle
}

private const val LAWN_MOWER = "lawn_mower"
private const val MOWING = "mowing"
private const val UNAVAILABLE = "unavailable"
private const val STRINGS = "ui.dialogs.more_info_control.lawn_mower"
private const val FEATURE_START_MOWING = 1
private const val FEATURE_PAUSE = 2
private const val FEATURE_DOCK = 4
