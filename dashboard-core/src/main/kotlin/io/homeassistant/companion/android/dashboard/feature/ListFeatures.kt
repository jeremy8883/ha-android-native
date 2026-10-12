package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject

// Ports of the dropdown-only mode features (frontend@20260624.6 src/panels/lovelace/card-features/): the select
// options and the media player's source and sound mode.

/** Port of `hui-select-options-card-feature`: always a dropdown, without icons. */
internal fun HassSnapshot.selectOptions(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "select" && state.domain != "input_select") return null
    return modeFeature(
        state,
        feature,
        ModeLook(
            label = localize("ui.card.select.option"),
            attribute = "option",
            domain = state.domain,
            service = "select_option",
            dropdownIcon = null,
            optionIcons = false,
            allowIcons = false,
        ),
        filterModes(state.list("options"), feature.configuredList("options")).map {
            ModeChoice(it, formatEntityState(state, it), null)
        },
        state.state,
    )
}

/**
 * Ports of `hui-media-player-source-card-feature` and `-sound-mode-`: a labelled dropdown without icons. The sound
 * mode needs a list of modes.
 */
internal fun HassSnapshot.mediaPlayerList(state: EntityState, feature: JsonObject, sound: Boolean): TileFeature? {
    val flag = if (sound) MEDIA_SOUND_MODE else MEDIA_SOURCE
    val attribute = if (sound) "sound_mode" else "source"
    val listAttribute = if (sound) "sound_mode_list" else "source_list"
    val supported = state.domain == MEDIA_PLAYER &&
        state.supportsFeature(flag) &&
        (!sound || state.list(listAttribute).isNotEmpty())
    if (!supported) return null
    return attributeModes(
        state,
        feature,
        ModeLook(
            label = localize("ui.card.media_player.$attribute"),
            attribute = attribute,
            domain = MEDIA_PLAYER,
            service = if (sound) "select_sound_mode" else "select_source",
            dropdownIcon = null,
            hideLabel = false,
            optionIcons = false,
            allowIcons = false,
        ),
        listAttribute,
        feature.configuredList(if (sound) "sound_modes" else "sources")?.ifEmpty { null },
    )
}

internal const val MEDIA_PLAYER = "media_player"
private const val MEDIA_SOURCE = 2048
private const val MEDIA_SOUND_MODE = 65536
