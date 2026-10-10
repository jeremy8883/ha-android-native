package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MediaControl
import io.homeassistant.companion.android.dashboard.derive.cleanupMediaTitle
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.mediaControls
import io.homeassistant.companion.android.dashboard.derive.mediaDescription
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `more-info-media_player` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-media_player.ts)
// with `computeMediaControls`, `handleMediaControlClick` and `formatMediaTime` (src/data/media-player.ts). Its
// volume is in MediaPlayerVolume.kt. Not ported: the media browser and the grouping dialog.

/**
 * What a media player's details show. Unavailable, only [unavailable] (the state) shows, in place of the artwork.
 *
 * @property picture the artwork, a path on the server (or a full URL); without one, the [state] shows instead
 * @property playing whether it plays (the artwork is larger)
 * @property main the transport buttons, when there are any
 * @property source the source menu, `null` when it can't choose one
 * @property soundMode the sound mode menu, `null` when it can't choose one or lists none
 */
data class MediaPlayerMoreInfo(
    val unavailable: String?,
    val state: String,
    val picture: String?,
    val playing: Boolean,
    val title: String?,
    val artist: String?,
    val position: MediaPosition?,
    val main: MediaMainControls?,
    val volume: MediaVolume?,
    val source: SelectMenu?,
    val soundMode: SelectMenu?,
    val turnOn: MediaControl?,
    val turnOff: MediaControl?,
)

/**
 * The track's position: [position] seconds when the server last reported it, at [updatedAt], counting up while
 * [ticking], out of [duration].
 */
data class MediaPosition(
    val position: Double,
    val updatedAt: Instant?,
    val ticking: Boolean,
    val duration: Double,
    val enabled: Boolean,
    val label: String,
    val seek: ValueService,
) {
    /** The position at [now], in whole seconds (`currentProgress`). */
    fun at(now: Instant): Double {
        val elapsed = updatedAt?.takeIf { ticking }?.let { (now.toEpochMilli() - it.toEpochMilli()) / MILLIS } ?: 0.0
        return maxOf(Math.floor(position + elapsed), 0.0)
    }

    /** Where the slider's handle is at [now]: the position, held at the end once past it. */
    fun sliderAt(now: Instant): Double = minOf(at(now), duration)
}

/** The transport buttons: repeat and previous on the [left], the play buttons in the [center], then the [right]. */
data class MediaMainControls(
    val left: List<MediaControl?>,
    val center: List<MediaControl>,
    val right: List<MediaControl?>,
)

/** The details of a media player, or `null` for another entity. */
fun HassSnapshot.mediaPlayerMoreInfo(state: EntityState): MediaPlayerMoreInfo? {
    if (state.domain != MEDIA_PLAYER) return null
    val text = formatEntityState(state)
    val controls = mediaControls(state, extended = true).map { (icon, action) -> mediaControl(state, icon, action) }
    val find = { action: String -> controls.firstOrNull { it.action.service == action } }
    return MediaPlayerMoreInfo(
        unavailable = text.takeIf { state.state == UNAVAILABLE },
        state = text,
        picture = (state.attributes.string("entity_picture_local") ?: state.attributes.string("entity_picture"))
            ?.ifEmpty { null },
        playing = state.state == PLAYING,
        title = cleanupMediaTitle(state.attributes.string("media_title")),
        artist = mediaDescription(state),
        position = mediaPosition(state),
        main = if (controls.isEmpty()) {
            null
        } else {
            MediaMainControls(
                left = listOf("repeat_set", "media_previous_track").map(find),
                center = CENTER_ACTIONS.mapNotNull(find),
                right = listOf("media_next_track", "shuffle_set").map(find),
            )
        },
        volume = mediaVolume(state),
        source = sourceMenu(state),
        soundMode = soundModeMenu(state),
        turnOn = find("turn_on"),
        turnOff = find("turn_off"),
    )
}

/** Port of `handleMediaControlClick`: shuffle toggles, repeat goes off → all → one → off. */
internal fun HassSnapshot.mediaControl(state: EntityState, icon: String, action: String): MediaControl {
    val extra = when (action) {
        "shuffle_set" -> "shuffle" to JsonPrimitive(state.attributes.boolean("shuffle") != true)
        "repeat_set" -> "repeat" to JsonPrimitive(NEXT_REPEAT[state.attributes.string("repeat")] ?: "off")
        else -> null
    }
    return MediaControl(
        label = localize("ui.card.media_player.$action"),
        icon = icon,
        action = CardAction.CallService(
            MEDIA_PLAYER,
            action,
            JsonObject(entityData(state) + listOfNotNull(extra)),
            target = null,
        ),
    )
}

private fun HassSnapshot.mediaPosition(state: EntityState): MediaPosition? {
    val duration = maxOf(state.attributes.numberOrNull("media_duration") ?: 0.0, 0.0).takeIf { it > 0 }
        ?: return null
    val updatedAt = state.attributes.string("media_position_updated_at")
        ?.let { parseJsDate(it, ZoneOffset.UTC) }
    val position = state.attributes.numberOrNull("media_position")
    val playing = state.state == PLAYING
    return MediaPosition(
        // Upstream's progress is NaN, so 0, without a position, or playing without its time
        position = position?.takeIf { !playing || updatedAt != null } ?: 0.0,
        updatedAt = updatedAt,
        ticking = playing && position != null && updatedAt != null,
        duration = duration,
        enabled = state.isActive() && state.supportsFeature(FEATURE_SEEK),
        label = localize("ui.card.media_player.track_position"),
        seek = ValueService(MEDIA_PLAYER, "media_seek", entityData(state), "seek_position"),
    )
}

/** Port of `formatMediaTime`: m:ss as `mm:ss`, or `hh:mm:ss` from an hour. */
fun formatMediaTime(seconds: Double): String {
    val total = maxOf(0L, Math.floor(seconds).toLong())
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = total % SECONDS_PER_MINUTE
    val pad = { value: Long -> value.toString().padStart(2, '0') }
    return if (hours > 0) "${pad(hours)}:${pad(minutes)}:${pad(secs)}" else "${pad(minutes)}:${pad(secs)}"
}

internal const val MEDIA_PLAYER = "media_player"
private const val PLAYING = "playing"
private const val UNAVAILABLE = "unavailable"
private const val MILLIS = 1000.0
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private const val FEATURE_SEEK = 2
private val CENTER_ACTIONS = listOf("media_play_pause", "media_pause", "media_play", "media_stop")
private val NEXT_REPEAT = mapOf("all" to "one", "off" to "all")
