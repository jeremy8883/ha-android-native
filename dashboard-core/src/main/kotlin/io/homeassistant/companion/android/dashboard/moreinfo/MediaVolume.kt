package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MediaControl
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.assumedState
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The volume, source and sound mode controls of `more-info-media_player` (frontend@20260624.6
// src/dialogs/more-info/controls/more-info-media_player.ts).

/**
 * A media player's volume: the mute button, the step buttons (without a slider), and the slider at [level] percent
 * (with a speaker icon instead of the mute button when it can't mute).
 */
data class MediaVolume(
    val mute: MediaControl?,
    val down: MediaControl?,
    val up: MediaControl?,
    val level: Double?,
    val slider: ValueService?,
) {
    /** The call that sets the volume to [percent]. */
    fun setTo(percent: Double): CardAction.CallService? = slider?.withValue(percent / PERCENT)
}

/** The volume controls, shown while on (or only assumed) for a player that sets or steps its volume. */
internal fun HassSnapshot.mediaVolume(state: EntityState): MediaVolume? {
    val sets = state.supportsFeature(FEATURE_VOLUME_SET)
    val steps = state.supportsFeature(FEATURE_VOLUME_STEP)
    val shown = state.isActive() || state.assumedState()
    if (!(sets || steps) || !shown) return null
    val muted = state.attributes.boolean("is_volume_muted") == true
    val stepButton = { action: String, icon: String -> mediaControl(state, icon, action).takeIf { steps && !sets } }
    return MediaVolume(
        mute = if (state.supportsFeature(FEATURE_VOLUME_MUTE)) {
            MediaControl(
                label = localize("ui.card.media_player.${if (muted) "media_volume_unmute" else "media_volume_mute"}"),
                icon = if (muted) "mdi:volume-off" else "mdi:volume-high",
                action = CardAction.CallService(
                    MEDIA_PLAYER,
                    "volume_mute",
                    JsonObject(entityData(state) + ("is_volume_muted" to JsonPrimitive(!muted))),
                    target = null,
                ),
            )
        } else {
            null
        },
        down = stepButton(
            "volume_down",
            "mdi:volume-minus",
        )?.copy(label = localize("ui.card.media_player.media_volume_down")),
        up = stepButton("volume_up", "mdi:volume-plus")?.copy(label = localize("ui.card.media_player.media_volume_up")),
        level = state.attributes.numberOrNull("volume_level")?.let { it * PERCENT }.takeIf { sets },
        slider = ValueService(MEDIA_PLAYER, "volume_set", entityData(state), "volume_level").takeIf { sets },
    )
}

/** The source menu: each source of the list (none when it lists none, as upstream). */
internal fun HassSnapshot.sourceMenu(state: EntityState): SelectMenu? =
    if (state.supportsFeature(FEATURE_SELECT_SOURCE)) {
        listMenu(
            state,
            "source",
            (state.attributes["source_list"] as? JsonArray).strings(),
            "select_source",
            "mdi:login-variant",
        )
    } else {
        null
    }

/** The sound mode menu, when the player lists sound modes. */
internal fun HassSnapshot.soundModeMenu(state: EntityState): SelectMenu? {
    val modes = (state.attributes["sound_mode_list"] as? JsonArray).strings()
    if (!state.supportsFeature(FEATURE_SELECT_SOUND_MODE) || modes.isEmpty()) return null
    return listMenu(state, "sound_mode", modes, "select_sound_mode", "mdi:music-note-eighth")
}

private fun HassSnapshot.listMenu(
    state: EntityState,
    attribute: String,
    values: List<String>,
    service: String,
    icon: String,
): SelectMenu = SelectMenu(
    label = localize("ui.card.media_player.$attribute"),
    icon = icon,
    value = state.attributes.string(attribute),
    enabled = true,
    options = values.map { value ->
        MenuOption(
            value = value,
            label = formatEntityAttributeValue(state, attribute, JsonPrimitive(value)),
            icon = null,
            action = CardAction.CallService(
                MEDIA_PLAYER,
                service,
                JsonObject(entityData(state) + (attribute to JsonPrimitive(value))),
                target = null,
            ),
        )
    },
    optionIcons = false,
)

/** The strings of a list attribute; none when it's missing, as upstream's `|| []`. */
private fun JsonArray?.strings(): List<String> =
    this?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        .orEmpty()

private const val PERCENT = 100.0
private const val FEATURE_VOLUME_SET = 4
private const val FEATURE_VOLUME_MUTE = 8
private const val FEATURE_VOLUME_STEP = 1024
private const val FEATURE_SELECT_SOURCE = 2048
private const val FEATURE_SELECT_SOUND_MODE = 65536
