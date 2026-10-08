package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState

const val STATE_UNAVAILABLE = "unavailable"
const val STATE_UNKNOWN = "unknown"
const val STATE_OFF = "off"

/**
 * Domains whose state is a timestamp of the last activation, so any available state counts as active.
 * Port of `TIMESTAMP_STATE_DOMAINS` (frontend@20260624.6 src/common/const.ts).
 */
val TIMESTAMP_STATE_DOMAINS = setOf(
    "ai_task", "button", "conversation", "event", "image", "infrared", "input_button", "notify",
    "radio_frequency", "scene", "stt", "tag", "tts", "wake_word", "datetime",
)

/** Whether the entity's state is unavailable or unknown. */
fun EntityState.isUnavailableOrUnknown(): Boolean = state == STATE_UNAVAILABLE || state == STATE_UNKNOWN

/**
 * Whether [state] (default: the entity's current state) counts as "active", for example to colour it.
 * Port of `stateActive` (frontend@20260624.6 src/common/entity/state_active.ts).
 */
fun EntityState.isActive(state: String = this.state): Boolean {
    if (domain in TIMESTAMP_STATE_DOMAINS) return state != STATE_UNAVAILABLE
    if (state == STATE_UNAVAILABLE || state == STATE_UNKNOWN) return false
    // "off" is inactive for most domains; for alert it means acknowledged but still active
    if (state == STATE_OFF && domain != "alert") return false

    return when (domain) {
        "alarm_control_panel" -> state != "disarmed"
        "alert" -> state != "idle"
        "cover", "valve" -> state != "closed"
        "device_tracker", "person" -> state != "not_home"
        "lawn_mower" -> state !in setOf("docked", "paused")
        "lock" -> state != "locked"
        "media_player" -> state != "standby"
        "vacuum" -> state !in setOf("idle", "docked", "paused")
        "plant" -> state == "problem"
        "group" -> state in setOf("on", "home", "open", "locked", "problem")
        "timer" -> state == "active"
        "camera" -> state == "streaming"
        else -> true
    }
}
