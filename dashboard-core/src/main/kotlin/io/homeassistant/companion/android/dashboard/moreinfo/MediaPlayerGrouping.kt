package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.entityPickerDisplay
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `dialog-join-media-players` and `ha-media-player-toggle` (frontend@20260624.6 src/components/media-player/)
// with `mediaPlayerJoin` and `mediaPlayerUnjoin` (src/data/media-player.ts).

/**
 * Grouping a media player with others: the button (with how many are grouped when more than one), and the dialog
 * listing the player itself (always on) and the others of its integration that can group.
 *
 * @property members the other players grouped with it now, which the dialog starts with
 */
data class MediaGrouping(
    val label: String,
    val count: Int?,
    val player: GroupablePlayer,
    val candidates: List<GroupablePlayer>,
    val members: Set<String>,
    val title: String,
    val selectAllLabel: String,
    val applyLabel: String,
    val cancelLabel: String,
    private val entityId: String,
) {
    /** The calls applying [selected]: join them, then unjoin those grouped before and no longer chosen. */
    fun apply(selected: Set<String>): List<CardAction.CallService> {
        val target = JsonObject(mapOf("entity_id" to JsonPrimitive(entityId)))
        val join = CardAction.CallService(
            MEDIA_PLAYER,
            "join",
            JsonObject(mapOf("group_members" to JsonArray(selected.map(::JsonPrimitive)))),
            target = target,
        )
        val unjoin = (members - selected).map { member ->
            CardAction.CallService(
                MEDIA_PLAYER,
                "unjoin",
                JsonObject(emptyMap()),
                target = JsonObject(
                    mapOf(
                        "entity_id" to JsonPrimitive(member),
                    ),
                ),
            )
        }
        return listOf(join) + unjoin
    }
}

/** A player of the dialog: its icon (by whether it plays), name and "area › device". */
data class GroupablePlayer(val entityId: String, val icon: String, val name: String, val context: String?)

/** Grouping for [state], `null` when it can't group or is unavailable. */
internal fun HassSnapshot.mediaGrouping(state: EntityState): MediaGrouping? {
    if (state.state == "unavailable" || !state.supportsFeature(FEATURE_GROUPING)) return null
    val members = (state.attributes["group_members"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
    val platform = registries.entities[state.entityId]?.platform
    val candidates = registries.entities.values
        .filter { entry ->
            entry.entityId != state.entityId &&
                entry.entityId.startsWith("$MEDIA_PLAYER.") &&
                platform != null &&
                entry.platform == platform &&
                states[entry.entityId]?.supportsFeature(FEATURE_GROUPING) == true
        }
        .mapNotNull { states[it.entityId]?.let(::groupablePlayer) }
    return MediaGrouping(
        label = localize("ui.card.media_player.join"),
        count = members?.size?.takeIf { it > 1 },
        player = groupablePlayer(state),
        candidates = candidates,
        members = members.orEmpty().filter { it != state.entityId }.toSet(),
        title = localize("ui.card.media_player.media_players"),
        selectAllLabel = localize("ui.card.media_player.select_all"),
        applyLabel = localize("ui.common.apply"),
        cancelLabel = localize("ui.common.cancel"),
        entityId = state.entityId,
    )
}

private fun HassSnapshot.groupablePlayer(state: EntityState): GroupablePlayer {
    val (name, context) = entityPickerDisplay(state)
    val icon = when (state.state) {
        "playing" -> "mdi:speaker-play"
        "paused" -> "mdi:speaker-pause"
        else -> "mdi:speaker"
    }
    return GroupablePlayer(state.entityId, icon, name, context?.replace(" › ", " ▸ "))
}

private const val FEATURE_GROUPING = 524288
