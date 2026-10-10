package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.LightFavorites
import io.homeassistant.companion.android.dashboard.moreinfo.favoriteColorsUpdate
import io.homeassistant.companion.android.dashboard.moreinfo.lightCurrentColor
import io.homeassistant.companion.android.dashboard.moreinfo.lightFavorites

/** Which favourites dialog is open. */
private sealed interface FavoriteDialog {
    data object Add : FavoriteDialog

    data class Edit(val index: Int) : FavoriteDialog

    data class Delete(val index: Int) : FavoriteDialog
}

/**
 * A light's favourite colours under its controls, port of `ha-more-info-light-favorite-colors`: tapping one sets
 * the light; while editing (an admin's long press, or the header's menu) one can be added, edited, deleted or
 * moved, each change saved to the light's registry entry. Hidden until the entry is loaded, and when it has none.
 */
@Composable
internal fun LightFavoritesSection(state: EntityState, hass: HassSnapshot, onAction: (CardAction) -> Unit) {
    val viewModel = favoritesViewModel(state.entityId)
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val favorites = remember(state, entry, editMode) {
        (entry as? Loadable.Ready)?.value?.let { hass.lightFavorites(state, it, editMode) }
    }
    var dialog by remember { mutableStateOf<FavoriteDialog?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        favorites?.let {
            LightFavoritesRow(
                it,
                editMode,
                hass.user?.isAdmin == true,
                rowCallbacks(state.entityId, it, viewModel, onAction) { d ->
                    dialog =
                        d
                },
            )
        }
        error?.let { FavoritesErrorText(it) }
    }
    // Only the favourites shown can be changed: never save a list built on ones that didn't load
    val open = dialog
    if (open != null && favorites != null) {
        FavoriteDialogHost(open, state, hass, favorites, viewModel, onAction) { dialog = null }
    }
}

/** The open favourites dialog: adding, editing or deleting a favourite, each saved when confirmed. */
@Composable
private fun FavoriteDialogHost(
    open: FavoriteDialog,
    state: EntityState,
    hass: HassSnapshot,
    favorites: LightFavorites,
    viewModel: MoreInfoFavoritesViewModel,
    onAction: (CardAction) -> Unit,
    close: () -> Unit,
) {
    val colors = favorites.favorites.map { it.color }
    when (open) {
        FavoriteDialog.Add -> FavoriteColorDialog(
            state = state,
            hass = hass,
            title = hass.localize("$FAVORITE_STRINGS.add_title"),
            initial = lightCurrentColor(state),
            onAction = onAction,
            onSave = { color ->
                close()
                viewModel.save(favoriteColorsUpdate(state.entityId, colors + color))
            },
            onDismiss = { close() },
        )
        is FavoriteDialog.Edit -> FavoriteColorDialog(
            state = state,
            hass = hass,
            title = hass.localize("$FAVORITE_STRINGS.edit_title"),
            initial = colors.getOrNull(open.index),
            onAction = onAction,
            onSave = { color ->
                close()
                viewModel.save(
                    favoriteColorsUpdate(
                        state.entityId,
                        colors.toMutableList().also {
                            it[open.index] = color
                        },
                    ),
                )
            },
            onDismiss = {
                close()
                // Upstream sets the light back to the favourite when its edit is cancelled
                favorites.favorites.getOrNull(open.index)?.let { onAction(it.apply) }
            },
        )
        is FavoriteDialog.Delete -> ConfirmDialog(
            title = hass.localize("$FAVORITE_STRINGS.delete_confirm_title"),
            text = hass.localize("$FAVORITE_STRINGS.delete_confirm_text"),
            confirm = hass.localize("$FAVORITE_STRINGS.delete_confirm_action"),
            onConfirm = {
                close()
                viewModel.save(
                    favoriteColorsUpdate(
                        state.entityId,
                        colors.filterIndexed { index, _ ->
                            index !=
                                open.index
                        },
                    ),
                )
            },
            onDismiss = { close() },
        )
    }
}

/** The row's callbacks: applying, editing (which first sets the light to the favourite), deleting and moving. */
private fun rowCallbacks(
    entityId: String,
    favorites: LightFavorites,
    viewModel: MoreInfoFavoritesViewModel,
    onAction: (CardAction) -> Unit,
    open: (FavoriteDialog) -> Unit,
): FavoritesRowCallbacks {
    val colors = favorites.favorites.map { it.color }
    return FavoritesRowCallbacks(
        onApply = { index -> onAction(favorites.favorites[index].apply) },
        onEdit = { index ->
            onAction(favorites.favorites[index].apply)
            open(FavoriteDialog.Edit(index))
        },
        onDelete = { open(FavoriteDialog.Delete(it)) },
        onMove = { from, to ->
            viewModel.save(
                favoriteColorsUpdate(
                    entityId,
                    colors.toMutableList().also {
                        it.add(to, it.removeAt(from))
                    },
                ),
            )
        },
        onAdd = { open(FavoriteDialog.Add) },
        onStartEditing = { viewModel.setEditMode(true) },
        onDone = { viewModel.setEditMode(false) },
    )
}

private const val FAVORITE_STRINGS = "ui.dialogs.more_info_control.light.favorite_color"
