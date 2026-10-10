package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData

// Port of `more-info-lawn_mower` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-lawn_mower.ts) with
// its helpers (src/data/lawn_mower.ts). Not ported: the drawn mower (`ha-state-control-lawn_mower-status`); its
// icon pulses in its place while it mows or heads home.

/**
 * What a lawn mower's details show: the battery beside the header's time, its status, and the start/pause and dock
 * buttons.
 */
data class LawnMowerMoreInfo(
    val battery: DeviceBattery?,
    val status: StatusIcon,
    val busy: Boolean,
    val buttons: List<CommandButton>,
)

/** The details of a lawn mower, or `null` for another entity. */
fun HassSnapshot.lawnMowerMoreInfo(state: EntityState): LawnMowerMoreInfo? {
    if (state.domain != LAWN_MOWER) return null
    val available = state.state != UNAVAILABLE
    val mowing = state.state == MOWING
    val pause = mowing && state.supportsFeature(FEATURE_PAUSE)
    val call = { service: String -> CardAction.CallService(LAWN_MOWER, service, entityData(state), target = null) }
    return LawnMowerMoreInfo(
        battery = deviceBattery(state),
        status = StatusIcon(entityIcon(state.entityId).orEmpty(), stateColor(state)),
        busy = mowing || state.state == "returning",
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

private const val LAWN_MOWER = "lawn_mower"
private const val MOWING = "mowing"
private const val UNAVAILABLE = "unavailable"
private const val STRINGS = "ui.dialogs.more_info_control.lawn_mower"
private const val FEATURE_START_MOWING = 1
private const val FEATURE_PAUSE = 2
private const val FEATURE_DOCK = 4
