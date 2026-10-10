package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import java.net.URLDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Display-ready content of a media control card.
 *
 * @property picture the artwork, a path on the server (or a full URL)
 * @property title the media title, or the description when there is no title
 * @property subtitle the description under a title
 * @property off whether the player is off (dimmed upstream)
 * @property moreInfoLabel the label of the button opening the player's details
 */
data class MediaControlModel(
    val entityId: String,
    val name: String,
    val icon: String?,
    val picture: String?,
    val title: String?,
    val subtitle: String?,
    val off: Boolean,
    val unavailable: Boolean,
    val controls: List<MediaControl>,
    val moreInfoLabel: String,
)

/** A media player button. */
data class MediaControl(val label: String, val icon: String, val action: CardAction.CallService)

/**
 * Derive a media control card, or `null` when the entity does not exist.
 * Port of `HuiMediaControlCard.render` (frontend@20260624.6 src/panels/lovelace/cards/hui-media-control-card.ts)
 * with `computeMediaControls`, `computeMediaDescription` and `cleanupMediaTitle` (src/data/media-player.ts).
 * The progress bar and browse-media button are not ported yet.
 */
fun HassSnapshot.mediaControlModel(card: CardConfig): MediaControlModel? {
    val state = card.entity?.let(states::get) ?: return null
    val entityId = state.entityId
    val unavailable = state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN
    val title = cleanupMediaTitle(state.attributes.string("media_title"))
    val description = mediaDescription(state)
    return MediaControlModel(
        entityId = entityId,
        name = entityNameDisplay(state, card.json["name"]),
        icon = entityIcon(entityId),
        picture = (state.attributes.string("entity_picture_local") ?: state.attributes.string("entity_picture"))
            ?.ifEmpty { null },
        title = title ?: description,
        subtitle = description.takeIf { title != null },
        off = !state.isActive() && !unavailable,
        unavailable = unavailable,
        controls = mediaControls(state).map { (icon, action) ->
            MediaControl(
                label = localize("ui.card.media_player.$action"),
                icon = icon,
                action = CardAction.CallService(
                    "media_player",
                    action,
                    buildJsonObject { put("entity_id", entityId) },
                    target = null,
                ),
            )
        },
        moreInfoLabel = localize("ui.panel.lovelace.cards.show_more_info"),
    )
}

/**
 * Port of `computeMediaControls`: icon and service pairs, with the shuffle and repeat controls when [extended].
 */
internal fun mediaControls(state: EntityState, extended: Boolean = false): List<Pair<String, String>> {
    val assumed = (state.attributes["assumed_state"] as? JsonPrimitive)?.content == "true"
    return when {
        state.state == STATE_UNAVAILABLE -> emptyList()
        !state.isActive() && !assumed ->
            listOfNotNull(("mdi:power-standby" to "turn_on").takeIf { state.supportsFeature(TURN_ON) })
        else -> activeMediaControls(state, assumed, extended)
    }
}

private fun activeMediaControls(state: EntityState, assumed: Boolean, extended: Boolean): List<Pair<String, String>> {
    fun supports(feature: Int) = state.supportsFeature(feature)
    val playingOrPaused = state.state == "playing" || state.state == "paused" || assumed
    val track = playingOrPaused && extended
    return listOfNotNull(
        ("mdi:power-on" to "turn_on").takeIf { assumed && supports(TURN_ON) },
        ((if (assumed) "mdi:power-off" else "mdi:power-standby") to "turn_off").takeIf { supports(TURN_OFF) },
        shuffleControl(state).takeIf { track && supports(SHUFFLE_SET) },
        ("mdi:skip-previous" to "media_previous_track").takeIf { playingOrPaused && supports(PREVIOUS_TRACK) },
    ) + (if (assumed) assumedPlayControls(state) else listOfNotNull(playPauseControl(state))) + listOfNotNull(
        ("mdi:skip-next" to "media_next_track").takeIf { playingOrPaused && supports(NEXT_TRACK) },
        repeatControl(state).takeIf { track && supports(REPEAT_SET) },
    )
}

/** The separate play, pause and stop buttons of a player whose state is only assumed. */
private fun assumedPlayControls(state: EntityState): List<Pair<String, String>> = listOfNotNull(
    ("mdi:play" to "media_play").takeIf { state.supportsFeature(PLAY) },
    ("mdi:pause" to "media_pause").takeIf { state.supportsFeature(PAUSE) },
    ("mdi:stop" to "media_stop").takeIf { state.supportsFeature(STOP) },
)

private fun shuffleControl(state: EntityState) =
    (if (state.attributes.boolean("shuffle") == true) "mdi:shuffle" else "mdi:shuffle-disabled") to "shuffle_set"

private fun repeatControl(state: EntityState) = when (state.attributes.string("repeat")) {
    "all" -> "mdi:repeat"
    "one" -> "mdi:repeat-once"
    else -> "mdi:repeat-off"
} to "repeat_set"

/** The play/pause button of a player whose state is known (not assumed), when it has one. */
private fun playPauseControl(state: EntityState): Pair<String, String>? {
    val value = state.state
    return when {
        !canPlayPause(state) -> null
        value == "on" -> "mdi:play-pause" to "media_play"
        value != "playing" -> "mdi:play" to "media_play"
        state.supportsFeature(PAUSE) -> "mdi:pause" to "media_pause"
        else -> "mdi:stop" to "media_stop"
    }
}

private fun canPlayPause(state: EntityState): Boolean {
    fun supports(feature: Int) = state.supportsFeature(feature)
    return when (state.state) {
        "playing" -> supports(PAUSE) || supports(STOP)
        "paused", "idle" -> supports(PLAY)
        "on" -> supports(PLAY) || supports(PAUSE)
        else -> false
    }
}

/** Port of `computeMediaDescription`. */
internal fun mediaDescription(state: EntityState): String? {
    val attributes = state.attributes
    val description = when (attributes.string("media_content_type")) {
        "music", "image" -> attributes.string("media_artist")
        "playlist" -> attributes.string("media_playlist")?.ifEmpty { null } ?: attributes.string("media_artist")
        "tvshow" -> {
            val season = attributes["media_season"]?.let { (it as? JsonPrimitive)?.content }?.takeIf {
                it.isNotEmpty() &&
                    it != "0"
            }
            val episode = attributes["media_episode"]?.let { (it as? JsonPrimitive)?.content }?.takeIf {
                it.isNotEmpty() &&
                    it != "0"
            }
            attributes.string("media_series_title").orEmpty() +
                (season?.let { " S$it" + (episode?.let { e -> "E$e" }.orEmpty()) }.orEmpty())
        }
        "channel" -> attributes.string("media_channel")
        else -> attributes.string("app_name")
    }
    return description?.ifEmpty { null }
}

/** Port of `cleanupMediaTitle`: drop signed URL parameters and show only the file name of URL titles. */
internal fun cleanupMediaTitle(title: String?): String? {
    if (title.isNullOrEmpty()) return null
    val index = title.indexOf("?authSig=")
    var clean = if (index > 0) title.substring(0, index) else title
    if (clean.startsWith("http")) clean = URLDecoder.decode(clean.substringAfterLast('/'), Charsets.UTF_8)
    return clean
}

// MediaPlayerEntityFeature (src/data/media-player.ts)
private const val PAUSE = 1
private const val PREVIOUS_TRACK = 16
private const val NEXT_TRACK = 32
private const val TURN_ON = 128
private const val TURN_OFF = 256
private const val STOP = 4096
private const val PLAY = 16384
private const val SHUFFLE_SET = 32768
private const val REPEAT_SET = 262144
