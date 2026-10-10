package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ALARM_MODES
import io.homeassistant.companion.android.dashboard.feature.ALARM_MODE_ICONS
import io.homeassistant.companion.android.dashboard.feature.alarmCall

// Port of `more-info-alarm_control_panel` (frontend@20260624.6 src/dialogs/more-info/controls/) with
// `ha-state-control-alarm_control_panel-modes` (src/state-control/alarm_control_panel/).

/**
 * What an alarm panel's details show: its modes as tall buttons, or, while it's triggered, arming or pending, its
 * pulsing icon and a disarm button.
 */
data class AlarmMoreInfo(
    val modes: ControlSelect?,
    val status: StatusIcon?,
    val disarm: Pair<String, CardAction.CallService>?,
)

/** An entity's icon in its colour, pulsing while something is under way. */
data class StatusIcon(val icon: String, val color: DisplayColor?)

/** The details of an alarm panel, or `null` for another entity. */
fun HassSnapshot.alarmMoreInfo(state: EntityState): AlarmMoreInfo? {
    if (state.domain != ALARM) return null
    val busy = state.state in BUSY_STATES
    return AlarmMoreInfo(
        modes = if (busy) null else alarmModes(state),
        status = if (busy) StatusIcon(entityIcon(state.entityId).orEmpty(), stateColor(state)) else null,
        disarm = if (busy) localize("ui.card.alarm_control_panel.disarm") to alarmCall(state, DISARMED) else null,
    )
}

private fun HassSnapshot.alarmModes(state: EntityState): ControlSelect {
    val modes = ALARM_MODES.filter { (_, mode) -> mode.second?.let { state.supportsFeature(it) } ?: true }.keys
    return ControlSelect(
        label = localize("ui.card.alarm_control_panel.modes_label"),
        value = modes.firstOrNull { it == state.state },
        enabled = state.state != UNAVAILABLE,
        color = stateColor(state),
        background = null,
        options = modes.map { mode ->
            MenuOption(
                mode,
                localize("ui.card.alarm_control_panel.modes.$mode"),
                ALARM_MODE_ICONS.getValue(mode),
                alarmCall(state, mode),
            )
        },
    )
}

private const val ALARM = "alarm_control_panel"
private const val DISARMED = "disarmed"
private const val UNAVAILABLE = "unavailable"
private val BUSY_STATES = setOf("triggered", "arming", "pending")
