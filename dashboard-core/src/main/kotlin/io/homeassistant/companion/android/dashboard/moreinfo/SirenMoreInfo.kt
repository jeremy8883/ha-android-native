package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of `more-info-siren` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-siren.ts) with its
// advanced controls (src/dialogs/more-info/components/siren/), and `more-info-remote`.

/** A siren's details: the tall switch, and the advanced controls when it takes a tone, volume or duration. */
data class SirenMoreInfo(val toggle: StateToggle, val advanced: SirenAdvanced?)

/**
 * The siren's advanced controls: a tone (value and name), a volume (percent) and a duration (seconds), each when
 * supported, and buttons turning it on with them or off.
 */
data class SirenAdvanced(
    val title: String,
    val tones: List<Pair<String, String>>?,
    val volume: Boolean,
    val duration: Boolean,
    val toneLabel: String,
    val volumeLabel: String,
    val durationLabel: String,
    val turnOnLabel: String,
    val turnOffLabel: String,
    val closeLabel: String,
    val turnOff: CardAction.CallService,
)

/** The details of a siren, or `null` for another entity. */
fun HassSnapshot.sirenMoreInfo(state: EntityState): SirenMoreInfo? {
    if (state.domain != SIREN) return null
    val tones = sirenTones(state).takeIf { state.supportsFeature(FEATURE_TONES) }
    val volume = state.supportsFeature(FEATURE_VOLUME_SET)
    val duration = state.supportsFeature(FEATURE_DURATION)
    return SirenMoreInfo(
        toggle = stateToggle(state, "mdi:volume-high", "mdi:volume-off"),
        advanced = if (tones != null || volume || duration) {
            SirenAdvanced(
                title = localize("ui.components.siren.more_controls"),
                tones = tones,
                volume = volume,
                duration = duration,
                toneLabel = localize("ui.components.siren.tone"),
                volumeLabel = localize("ui.components.siren.volume"),
                durationLabel = localize("ui.components.siren.duration"),
                turnOnLabel = localize("ui.card.common.turn_on"),
                turnOffLabel = localize("ui.card.common.turn_off"),
                closeLabel = localize("ui.common.close"),
                turnOff = CardAction.CallService(SIREN, "turn_off", entityData(state), target = null),
            )
        } else {
            null
        },
    )
}

/** The call turning [state] on with the chosen [tone], [volume] (0–1) and [duration] (seconds), each when set. */
fun sirenTurnOnCall(state: EntityState, tone: String?, volume: Double?, duration: Int?): CardAction.CallService {
    val options = listOfNotNull(
        tone?.let { "tone" to JsonPrimitive(it) },
        volume?.let { "volume_level" to JsonPrimitive(it) },
        duration?.let { "duration" to JsonPrimitive(it) },
    )
    return CardAction.CallService(SIREN, "turn_on", JsonObject(entityData(state) + options), target = null)
}

/** The tones: a list's names are their values, a map's keys are. */
private fun sirenTones(state: EntityState): List<Pair<String, String>>? =
    when (val tones = state.attributes["available_tones"]) {
        is JsonArray -> tones.mapNotNull { (it as? JsonPrimitive)?.content }.map { it to it }
        is JsonObject -> tones.mapNotNull { (key, name) -> (name as? JsonPrimitive)?.content?.let { key to it } }
        else -> null
    }

/** A remote's activity menu, when it supports activities; `null` for another entity or without. */
fun HassSnapshot.remoteActivity(state: EntityState): SelectMenu? {
    if (state.domain != REMOTE || !state.supportsFeature(FEATURE_ACTIVITY)) return null
    val activities = (state.attributes["activity_list"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
    return SelectMenu(
        label = localize("ui.dialogs.more_info_control.remote.activity"),
        icon = "mdi:remote",
        value = state.attributes.string("current_activity"),
        enabled = true,
        options = activities.orEmpty().map { activity ->
            MenuOption(
                value = activity,
                label = formatEntityAttributeValue(state, "activity", JsonPrimitive(activity)),
                icon = null,
                action = CardAction.CallService(
                    REMOTE,
                    "turn_on",
                    JsonObject(entityData(state) + ("activity" to JsonPrimitive(activity))),
                    target = null,
                ),
            )
        },
        optionIcons = false,
    )
}

private const val SIREN = "siren"
private const val REMOTE = "remote"
private const val FEATURE_TONES = 4
private const val FEATURE_VOLUME_SET = 8
private const val FEATURE_DURATION = 16
private const val FEATURE_ACTIVITY = 4
