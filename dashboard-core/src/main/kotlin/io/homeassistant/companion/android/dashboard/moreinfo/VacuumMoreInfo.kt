package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string

// Port of `more-info-vacuum` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-vacuum.ts) with
// `ha-state-control-vacuum-status` (src/state-control/vacuum/) and its helpers (src/data/vacuum.ts). The battery is
// in DeviceBattery.kt, cleaning by area in VacuumCleanAreas.kt.

/**
 * What a vacuum's details show: the state (or its status), the battery beside it, the robot drawn as it is
 * ([visual], in [color]), the command buttons and the fan speed menu.
 */
data class VacuumMoreInfo(
    val state: String,
    val battery: DeviceBattery?,
    val visual: VacuumVisual,
    val color: DisplayColor?,
    val buttons: List<CommandButton>,
    val fanSpeed: SelectMenu?,
    val cleanAreasLabel: Pair<String, String>?,
)

/** One command button. */
data class CommandButton(val label: String, val icon: String, val enabled: Boolean, val action: CardAction.CallService)

/** What the drawn robot does (`computeVisualState`). */
sealed interface VacuumVisual {
    /** Wandering about with its brush spinning, sucking up dust. */
    data object Cleaning : VacuumVisual

    /** Turned around, heading for its dock. */
    data object Returning : VacuumVisual

    /** Still, at full strength. */
    data object Paused : VacuumVisual

    /** On its dock, charging. */
    data object Docked : VacuumVisual

    /** Glowing a warning. */
    data object Error : VacuumVisual

    /** Faded. */
    data object Idle : VacuumVisual
}

/** The details of a vacuum, or `null` for another entity. */
fun HassSnapshot.vacuumMoreInfo(state: EntityState): VacuumMoreInfo? {
    if (state.domain != VACUUM) return null
    val status = state.attributes.string("status")?.ifEmpty { null }
    return VacuumMoreInfo(
        state = if (state.supportsFeature(VACUUM_STATUS) && status != null) {
            formatEntityAttributeValue(state, "status")
        } else {
            formatEntityState(state)
        },
        battery = vacuumBattery(state),
        visual = vacuumVisual(state),
        color = stateColor(state),
        buttons = vacuumButtons(state),
        // "Cleaning · By area", which opens the areas to clean
        cleanAreasLabel = (localize("$STRINGS.cleaning") to localize("$STRINGS.by_area"))
            .takeIf { state.supportsFeature(VACUUM_CLEAN_AREA) },
        fanSpeed = if (state.supportsFeature(VACUUM_FAN_SPEED)) {
            attributeMenu(state, "fan_speed", "fan_speed_list", "set_fan_speed", "mdi:fan")
                ?.copy(label = attributeName(state, "fan_speed"), optionIcons = false)
        } else {
            null
        },
    )
}

private fun vacuumVisual(state: EntityState): VacuumVisual = when {
    state.state == UNAVAILABLE -> VacuumVisual.Idle
    state.state == "error" -> VacuumVisual.Error
    isCleaning(state) -> VacuumVisual.Cleaning
    state.state == "returning" -> VacuumVisual.Returning
    state.state == "paused" -> VacuumVisual.Paused
    state.state == "docked" -> VacuumVisual.Docked
    else -> VacuumVisual.Idle
}

private fun HassSnapshot.vacuumButtons(state: EntityState): List<CommandButton> {
    val unavailable = state.state == UNAVAILABLE
    val supports = { feature: Int -> state.supportsFeature(feature) }
    val button = { key: String, icon: String, enabled: Boolean, service: String ->
        CommandButton(localize("$STRINGS.$key"), icon, enabled, call(state, service))
    }
    return listOfNotNull(
        startPauseButton(state).takeIf { supports(VACUUM_START) || supports(VACUUM_PAUSE) },
        button("stop", "mdi:stop", !unavailable && state.state !in STOPPED, "stop").takeIf { supports(VACUUM_STOP) },
        button("return_home", "mdi:home-import-outline", !unavailable && state.state != "returning", "return_to_base")
            .takeIf { supports(VACUUM_RETURN_HOME) },
        button("locate", "mdi:map-marker", !unavailable, "locate").takeIf { supports(VACUUM_LOCATE) },
        button("clean_spot", "mdi:target-variant", !unavailable, "clean_spot").takeIf { supports(VACUUM_CLEAN_SPOT) },
    )
}

/**
 * Start, or pause while cleaning; a legacy vacuum (neither state nor start supported) toggles with `start_pause`.
 */
private fun HassSnapshot.startPauseButton(state: EntityState): CommandButton {
    val legacy = !state.supportsFeature(VACUUM_STATE) && !state.supportsFeature(VACUUM_START)
    val pause = isCleaning(state) && state.supportsFeature(VACUUM_PAUSE)
    val service = when {
        legacy && state.supportsFeature(VACUUM_PAUSE) -> "start_pause"
        isCleaning(state) -> "pause"
        else -> "start"
    }
    return CommandButton(
        label = localize(
            "$STRINGS.${if (legacy) {
                "start_pause"
            } else if (pause) {
                "pause"
            } else {
                "start"
            }}",
        ),
        icon = if (legacy) {
            "mdi:play-pause"
        } else if (pause) {
            "mdi:pause"
        } else {
            "mdi:play"
        },
        // Upstream's `canStart` holds whenever it's available and not cleaning, so only unavailable disables it
        enabled = state.state != UNAVAILABLE,
        action = call(state, service),
    )
}

private fun isCleaning(state: EntityState) = state.state == "cleaning" || state.state == "on"

private fun call(state: EntityState, service: String) =
    CardAction.CallService(VACUUM, service, entityData(state), target = null)

private const val VACUUM = "vacuum"
private const val UNAVAILABLE = "unavailable"
private const val STRINGS = "ui.dialogs.more_info_control.vacuum"
private const val VACUUM_PAUSE = 4
private const val VACUUM_STOP = 8
private const val VACUUM_RETURN_HOME = 16
private const val VACUUM_FAN_SPEED = 32
private const val VACUUM_STATUS = 128
private const val VACUUM_LOCATE = 512
private const val VACUUM_CLEAN_SPOT = 1024
private const val VACUUM_STATE = 4096
private const val VACUUM_START = 8192
private const val VACUUM_CLEAN_AREA = 16384
private val STOPPED = setOf("docked", "off", "idle")
