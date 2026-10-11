package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.boolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `hui-media-player-entity-row` (frontend@20260624.6
// src/panels/lovelace/entity-rows/hui-media-player-entity-row.ts), as wide as a phone's card (not `_narrow`).

/**
 * A media player's row controls: the power and transport buttons on the row ([top]), and while on with a volume, a
 * second line with the mute button and the volume ([volume]) then the transport buttons ([bottom]).
 */
data class RowMedia(val top: List<RowIconButton>, val volume: RowVolume?, val bottom: List<RowIconButton>) : RowControl

/**
 * The volume: the mute button (when it can mute), and a slider ([level], 0–100, set with [setCall]) or down and up
 * buttons ([steps]).
 */
data class RowVolume(
    val mute: RowIconButton?,
    val level: Double?,
    val steps: List<RowIconButton>,
    private val entityId: String,
) {
    /** Set the volume to [percent]. */
    fun setCall(percent: Double): CardAction.CallService = CardAction.CallService(
        "media_player",
        "volume_set",
        JsonObject(
            mapOf("entity_id" to JsonPrimitive(entityId), "volume_level" to JsonPrimitive(percent / PERCENT)),
        ),
        target = null,
    )
}

/** The row of a media player: its media (else state) under the name, and its controls. */
internal fun HassSnapshot.mediaRow(state: EntityState): DomainRow {
    val assumed = state.attributes.boolean("assumed_state") == true
    val active = state.isActive()
    val transport = transportButtons(state, assumed)
    val hasVolume = state.supportsFeature(VOLUME_SET) || state.supportsFeature(VOLUME_STEP)
    // The transport buttons sit on the row unless the volume line takes them
    val rowTransport =
        !hasVolume && (active || assumed || !state.supportsFeature(TURN_ON) || state.state == STATE_UNAVAILABLE)
    val (turnOn, turnOff) = powerButtons(state, assumed, active)
    val volume = rowVolume(state).takeIf { hasVolume && active }
    return DomainRow(
        control = RowMedia(
            top = listOfNotNull(turnOn) + transport.takeIf { rowTransport }.orEmpty() + listOfNotNull(turnOff),
            volume = volume,
            bottom = if (volume != null) transport else emptyList(),
        ),
        nameSecondary = mediaDescription(state) ?: formatEntityState(state),
    )
}

/** Turn on (while off, or only assumed) and turn off (while on, or only assumed), as the player supports them. */
private fun HassSnapshot.powerButtons(
    state: EntityState,
    assumed: Boolean,
    active: Boolean,
): Pair<RowIconButton?, RowIconButton?> {
    val on = mediaButton(state, if (assumed) "mdi:power-on" else "mdi:power-standby", "turn_on", "turn_on")
        .takeIf { state.supportsFeature(TURN_ON) && (!active || assumed) && state.state != STATE_UNAVAILABLE }
    val off = mediaButton(state, if (assumed) "mdi:power-off" else "mdi:power-standby", "turn_off", "turn_off")
        .takeIf { state.supportsFeature(TURN_OFF) && (active || assumed) }
    return on to off
}

/** The volume line's mute, slider or steps. */
private fun HassSnapshot.rowVolume(state: EntityState): RowVolume {
    val muted = state.attributes.boolean("is_volume_muted") == true
    val stepsOnly = !state.supportsFeature(VOLUME_SET) && state.supportsFeature(VOLUME_STEP)
    return RowVolume(
        mute = mediaButton(
            state,
            if (muted) "mdi:volume-off" else "mdi:volume-high",
            if (muted) "media_volume_mute" else "media_volume_unmute",
            "volume_mute",
            mapOf("is_volume_muted" to JsonPrimitive(!muted)),
        ).takeIf { state.supportsFeature(VOLUME_MUTE) },
        level = (number(state.attributes["volume_level"]) ?: 0.0).times(PERCENT).takeIf {
            state.supportsFeature(VOLUME_SET)
        },
        steps = if (stepsOnly) {
            listOf(
                mediaButton(state, "mdi:volume-minus", "media_volume_down", "volume_down"),
                mediaButton(state, "mdi:volume-plus", "media_volume_up", "volume_up"),
            )
        } else {
            emptyList()
        },
        entityId = state.entityId,
    )
}

/** The previous, play/pause/stop (or an assumed player's play, pause and stop) and next buttons. */
private fun HassSnapshot.transportButtons(state: EntityState, assumed: Boolean): List<RowIconButton> {
    val supports = state::supportsFeature
    val playing = state.state == "playing"
    return listOfNotNull(
        mediaButton(state, "mdi:skip-previous", "media_previous_track", "media_previous_track")
            .takeIf { (playing || assumed) && supports(PREVIOUS) },
        playPauseButton(state).takeIf { !assumed && canPlayPause(state) },
        mediaButton(state, "mdi:play", "media_play", "media_play").takeIf { assumed && supports(PLAY) },
        mediaButton(state, "mdi:pause", "media_pause", "media_pause").takeIf { assumed && supports(PAUSE) },
        mediaButton(state, "mdi:stop", "media_stop", "media_stop").takeIf {
            assumed &&
                supports(STOP) &&
                !supports(VOLUME_SET)
        },
        mediaButton(state, "mdi:skip-next", "media_next_track", "media_next_track")
            .takeIf { (playing || (assumed && !supports(VOLUME_SET))) && supports(NEXT) },
    )
}

/** Whether the player's state has a play, pause or stop to offer. */
private fun canPlayPause(state: EntityState): Boolean = when (state.state) {
    "playing" -> state.supportsFeature(PAUSE) || state.supportsFeature(STOP)
    "paused", "idle" -> state.supportsFeature(PLAY)
    "on" -> state.supportsFeature(PLAY) || state.supportsFeature(PAUSE)
    else -> false
}

/** `_computeControlButton`'s icon, with `_playPauseStop`'s call (play unless playing). */
private fun HassSnapshot.playPauseButton(state: EntityState): RowIconButton {
    val playing = state.state == "playing"
    val (icon, key) = when {
        state.state == "on" -> "mdi:play-pause" to "media_play_pause"
        !playing -> "mdi:play" to "media_play"
        state.supportsFeature(PAUSE) -> "mdi:pause" to "media_pause"
        else -> "mdi:stop" to "media_stop"
    }
    val service = if (!playing) {
        "media_play"
    } else if (state.supportsFeature(PAUSE)) {
        "media_pause"
    } else {
        "media_stop"
    }
    return mediaButton(state, icon, key, service)
}

private fun HassSnapshot.mediaButton(
    state: EntityState,
    icon: String,
    key: String,
    service: String,
    extra: Map<String, JsonPrimitive> = emptyMap(),
) = RowIconButton(
    icon,
    localize("ui.card.media_player.$key"),
    enabled = true,
    shown = true,
    active = false,
    mediaCall(state, service, extra),
)

private const val PERCENT = 100.0
private const val PAUSE = 1
private const val VOLUME_SET = 4
private const val VOLUME_MUTE = 8
private const val PREVIOUS = 16
private const val NEXT = 32
private const val TURN_ON = 128
private const val TURN_OFF = 256
private const val VOLUME_STEP = 1024
private const val STOP = 4096
private const val PLAY = 16384
