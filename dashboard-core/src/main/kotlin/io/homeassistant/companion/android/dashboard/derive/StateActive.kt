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
fun EntityState.isActive(state: String = this.state): Boolean = when {
    domain in TIMESTAMP_STATE_DOMAINS -> state != STATE_UNAVAILABLE
    state == STATE_UNAVAILABLE || state == STATE_UNKNOWN -> false
    // "off" is inactive for most domains; for alert it means acknowledged but still active
    state == STATE_OFF && domain != "alert" -> false
    else -> ACTIVE_STATES[domain]?.let { state in it } ?: INACTIVE_STATES[domain]?.let { state !in it } ?: true
}

/** The states in which an entity of these domains is inactive; any other is active. */
private val INACTIVE_STATES = mapOf(
    "alarm_control_panel" to setOf("disarmed"),
    "alert" to setOf("idle"),
    "cover" to setOf("closed"),
    "valve" to setOf("closed"),
    "device_tracker" to setOf("not_home"),
    "person" to setOf("not_home"),
    "lawn_mower" to setOf("docked", "paused"),
    "lock" to setOf("locked"),
    "media_player" to setOf("standby"),
    "vacuum" to setOf("idle", "docked", "paused"),
)

/** The only states in which an entity of these domains is active. */
private val ACTIVE_STATES = mapOf(
    "plant" to setOf("problem"),
    "group" to setOf("on", "home", "open", "locked", "problem"),
    "timer" to setOf("active"),
    "camera" to setOf("streaming"),
)
