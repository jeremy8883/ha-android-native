package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of `hui-media-player-playback-card-feature` with `media-player-playback-controls.ts`, and of
// `hui-date-set-card-feature` (frontend@20260624.6 src/panels/lovelace/card-features/).

/**
 * The playback buttons: the configured `controls`, else power, previous, play/pause and next (separate on, off,
 * play and pause for players whose state is only assumed). Buttons that don't apply now are disabled, or left out
 * with `hide_disabled_controls`; previous and next are dropped on a narrow tile with more than three buttons.
 */
internal fun HassSnapshot.mediaPlayerPlayback(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != MEDIA_PLAYER) return null
    val assumed = state.attributes.boolean("assumed_state") == true
    val controls = feature.configuredList("controls") ?: if (assumed) ASSUMED_CONTROLS else DEFAULT_CONTROLS
    val buttons = controls.filter { control ->
        PLAYBACK_FLAGS[control]?.any { state.supportsFeature(it) } == true
    }.map { playbackButton(state, it) }
    val shown = if (feature.boolean("hide_disabled_controls") == true) buttons.filter { it.enabled } else buttons
    return TileFeature.Buttons(shown)
}

private fun HassSnapshot.playbackButton(state: EntityState, control: String): TileFeature.Button {
    val (icon, service, disabled) = playbackSpec(state, control)
    val data: Map<String, JsonPrimitive> = when (control) {
        "volume_mute" -> mapOf("is_volume_muted" to JsonPrimitive(state.attributes.boolean("is_volume_muted") != true))
        "shuffle" -> mapOf("shuffle" to JsonPrimitive(state.attributes.boolean("shuffle") != true))
        "repeat" -> mapOf("repeat" to JsonPrimitive(nextRepeat(state.attributes.string("repeat") ?: "off")))
        else -> emptyMap()
    }
    return TileFeature.Button(
        // Upstream names the label after the control's action: `shuffle` and `repeat` for their services
        label = localize("ui.card.media_player.${PLAYBACK_LABELS[service] ?: service}"),
        icon = icon,
        enabled = !disabled,
        action = CardAction.CallService(MEDIA_PLAYER, service, JsonObject(entityData(state) + data), target = null),
        hiddenWhenNarrow = service == "media_previous_track" || service == "media_next_track",
    )
}

private fun nextRepeat(repeat: String) = when (repeat) {
    "off" -> "one"
    "one" -> "all"
    else -> "off"
}

/** A control's icon, its service (the toggles resolved by state) and whether it's disabled now. */
private data class PlaybackSpec(val icon: String, val service: String, val disabled: Boolean)

/** What the playback buttons depend on: the player, and whether it's on, playing, has media, or is assumed. */
private data class Playback(val state: EntityState) {
    val active = state.isActive()
    val assumed = state.attributes.boolean("assumed_state") == true
    val playing = state.state == "playing"
    val context = playing || state.state == "paused" || assumed
    val unavailable = state.state == STATE_UNAVAILABLE
}

/** Port of `MEDIA_PLAYER_PLAYBACK_CONTROLS_BUTTONS`. */
private fun playbackSpec(state: EntityState, control: String): PlaybackSpec {
    val playback = Playback(state)
    return PLAYBACK_SPECS[control]?.invoke(playback) ?: repeatSpec(playback)
}

private val PLAYBACK_SPECS: Map<String, (Playback) -> PlaybackSpec> = mapOf(
    "turn_on" to { PlaybackSpec("mdi:power-on", "turn_on", it.unavailable || (it.active && !it.assumed)) },
    "turn_off" to { PlaybackSpec("mdi:power-off", "turn_off", !it.active && !it.assumed) },
    "power" to {
        PlaybackSpec(
            "mdi:power-standby",
            if (it.active) "turn_off" else "turn_on",
            it.unavailable || !it.state.supportsFeature(if (it.active) TURN_OFF else TURN_ON),
        )
    },
    "media_play" to { PlaybackSpec("mdi:play", "media_play", !it.assumed && (!it.active || it.playing)) },
    "media_pause" to { PlaybackSpec("mdi:pause", "media_pause", !it.playing && !it.assumed) },
    "media_play_pause" to { playPause(it.state, it.playing, it.active) },
    "media_stop" to { PlaybackSpec("mdi:stop", "media_stop", !it.context) },
    "media_previous_track" to { PlaybackSpec("mdi:skip-previous", "media_previous_track", !it.context) },
    "media_next_track" to { PlaybackSpec("mdi:skip-next", "media_next_track", !it.context) },
    "volume_down" to { PlaybackSpec("mdi:volume-minus", "volume_down", !it.active) },
    "volume_up" to { PlaybackSpec("mdi:volume-plus", "volume_up", !it.active) },
    "volume_mute" to {
        val muted = it.state.attributes.boolean("is_volume_muted") == true
        PlaybackSpec(if (muted) "mdi:volume-off" else "mdi:volume-high", "volume_mute", !it.active)
    },
    "shuffle" to {
        val on = it.state.attributes.boolean("shuffle") == true
        PlaybackSpec(if (on) "mdi:shuffle" else "mdi:shuffle-disabled", "shuffle_set", !it.context)
    },
)

private fun repeatSpec(playback: Playback) = PlaybackSpec(
    when (playback.state.attributes.string("repeat")) {
        "all" -> "mdi:repeat"
        "one" -> "mdi:repeat-once"
        else -> "mdi:repeat-off"
    },
    "repeat_set",
    !playback.context,
)

private fun playPause(state: EntityState, playing: Boolean, active: Boolean): PlaybackSpec {
    val canPause = state.supportsFeature(PAUSE)
    return when {
        !playing -> PlaybackSpec("mdi:play", "media_play", !active)
        canPause -> PlaybackSpec("mdi:pause", "media_pause", !active)
        else -> PlaybackSpec("mdi:stop", "media_stop", !active)
    }
}

/**
 * What picking a date sets: `input_datetime.set_datetime` or `date.set_value` with the date, or
 * `datetime.set_value` with the entity's time on the picked day.
 */
data class DateRequest(val entityId: String, val domain: String, val state: String) {
    /** The call setting [date], the day taken in [zone] (the device's, as the browser's upstream). */
    fun call(date: LocalDate, zone: ZoneId): CardAction.CallService {
        val data = buildMap {
            put("entity_id", JsonPrimitive(entityId))
            if (domain == "datetime") {
                val current = parseInstant(state)?.atZone(zone) ?: return@buildMap
                val picked = current.withYear(date.year).withMonth(date.monthValue).withDayOfMonth(date.dayOfMonth)
                put("datetime", JsonPrimitive(ISO_UTC.format(picked.toInstant())))
            } else {
                put("date", JsonPrimitive(date.toString()))
            }
        }
        return CardAction.CallService(
            domain,
            if (domain == "input_datetime") "set_datetime" else "set_value",
            JsonObject(data),
            target = null,
        )
    }
}

private fun parseInstant(text: String): Instant? = try {
    java.time.OffsetDateTime.parse(text).toInstant()
} catch (_: DateTimeParseException) {
    null
}

/** Port of `hui-date-set-card-feature`: a button opening a date picker, for entities with a date. */
internal fun HassSnapshot.dateSet(state: EntityState): TileFeature? {
    val supported = (state.domain == "input_datetime" && state.attributes.boolean("has_date") == true) ||
        state.domain == "datetime" ||
        state.domain == "date"
    if (!supported) return null
    val date = if (state.domain == "datetime") {
        parseInstant(state.state)?.atZone(ZoneId.systemDefault())?.toLocalDate()
    } else {
        try {
            LocalDate.parse(state.state.take(ISO_DATE_LENGTH))
        } catch (_: DateTimeParseException) {
            null
        }
    }
    return TileFeature.DateSet(
        label = localize("ui.card.date.set_date"),
        enabled = state.state != STATE_UNAVAILABLE && state.state != "unknown",
        date = date,
        request = DateRequest(state.entityId, state.domain, state.state),
    )
}

private val PLAYBACK_LABELS = mapOf("shuffle_set" to "shuffle", "repeat_set" to "repeat")

/** `Date.toISOString`: UTC with milliseconds. */
private val ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"))
private const val ISO_DATE_LENGTH = 10

private val DEFAULT_CONTROLS = listOf("power", "media_previous_track", "media_play_pause", "media_next_track")
private val ASSUMED_CONTROLS =
    listOf("turn_on", "turn_off", "media_previous_track", "media_play", "media_pause", "media_next_track")

// MediaPlayerEntityFeature (src/data/media-player.ts)
private const val PAUSE = 1
private const val VOLUME_MUTE = 8
private const val PREVIOUS_TRACK = 16
private const val NEXT_TRACK = 32
private const val TURN_ON = 128
private const val TURN_OFF = 256
private const val VOLUME_STEP = 1024
private const val STOP = 4096
private const val PLAY = 16384
private const val SHUFFLE_SET = 32768
private const val REPEAT_SET = 262144

/** `MEDIA_PLAYER_PLAYBACK_CONTROLS_FEATURES`. */
private val PLAYBACK_FLAGS = mapOf(
    "turn_on" to listOf(TURN_ON),
    "turn_off" to listOf(TURN_OFF),
    "power" to listOf(TURN_ON, TURN_OFF),
    "media_play" to listOf(PLAY),
    "media_pause" to listOf(PAUSE),
    "media_play_pause" to listOf(PLAY, PAUSE),
    "media_stop" to listOf(STOP),
    "media_previous_track" to listOf(PREVIOUS_TRACK),
    "media_next_track" to listOf(NEXT_TRACK),
    "volume_down" to listOf(VOLUME_STEP),
    "volume_up" to listOf(VOLUME_STEP),
    "volume_mute" to listOf(VOLUME_MUTE),
    "shuffle" to listOf(SHUFFLE_SET),
    "repeat" to listOf(REPEAT_SET),
)
