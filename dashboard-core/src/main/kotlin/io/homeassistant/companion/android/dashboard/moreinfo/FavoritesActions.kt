package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject

// Port of the more-info dialog's favourites menu (frontend@20260624.6 src/dialogs/more-info/favorites.ts,
// `getFavoritesDialogHandler`): edit, reset and copy the favourites of a light, cover or valve.

/**
 * What the header's favourites menu offers for an entity: editing, resetting (when some were saved, [custom]) and
 * copying them to [copyTargets], with the domain's labels.
 *
 * @property copy the commands that copy the favourites to the chosen lights or covers
 */
data class FavoritesActions(
    val editLabel: String,
    val resetLabel: String,
    val resetText: String,
    val copyLabel: String,
    val copyHelper: String,
    val custom: Boolean,
    val reset: WsCommand,
    val copyTargets: List<String>,
    val copy: (List<String>) -> List<WsCommand>,
)

/** The favourites menu of [state] given its registry [entry], or `null` when it has no favourites. */
fun HassSnapshot.favoritesActions(state: EntityState, entry: JsonObject): FavoritesActions? {
    val strings = "ui.dialogs.more_info_control.${state.domain}"
    fun actions(custom: Boolean, reset: WsCommand, targets: List<String>, copy: (List<String>) -> List<WsCommand>) =
        FavoritesActions(
            editLabel = localize("$strings.edit_mode"),
            resetLabel = localize("$strings.reset_favorites"),
            resetText = localize("$strings.reset_favorites_text"),
            copyLabel = localize("$strings.copy_favorites"),
            copyHelper = localize("$strings.copy_favorites_helper"),
            custom = custom,
            reset = reset,
            copyTargets = targets,
            copy = copy,
        )
    return when (state.domain) {
        "light" -> lightFavorites(state, entry, editMode = true)?.takeIf {
            lightTakesFavorites(state)
        }?.let { favorites ->
            val colors = favorites.favorites.map { it.color }
            actions(
                favorites.custom,
                favoriteColorsUpdate(state.entityId, null),
                favoriteCopyTargets(state.entityId, colors),
            ) { targets ->
                targets.map { favoriteColorsUpdate(it, colors) }
            }
        }
        COVER, VALVE -> positionFavorites(state, entry, editMode = true)
            ?.takeIf {
                state.supportsFeature(FEATURE_SET_POSITION) ||
                    state.supportsFeature(FEATURE_SET_TILT_POSITION) &&
                    state.domain == COVER
            }
            ?.let { favorites ->
                actions(
                    favorites.custom,
                    positionFavoritesReset(state),
                    positionFavoriteCopyTargets(state),
                ) { targets ->
                    positionFavoritesCopy(state, entry, targets)
                }
            }
        else -> null
    }
}
