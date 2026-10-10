package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.LightColor
import io.homeassistant.companion.android.dashboard.moreinfo.LightFavorites
import io.homeassistant.companion.android.dashboard.moreinfo.favoriteCopyTargets
import io.homeassistant.companion.android.dashboard.moreinfo.lightCurrentColor
import io.homeassistant.companion.android.dashboard.moreinfo.lightFavorites
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.loadErrorText

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
    val viewModel = lightViewModel(state.entityId)
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
                rowCallbacks(it, viewModel, onAction) { d ->
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
    viewModel: MoreInfoLightViewModel,
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
                viewModel.save(colors + color)
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
                viewModel.save(colors.toMutableList().also { it[open.index] = color })
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
                viewModel.save(colors.filterIndexed { index, _ -> index != open.index })
            },
            onDismiss = { close() },
        )
    }
}

/** The row's callbacks: applying, editing (which first sets the light to the favourite), deleting and moving. */
private fun rowCallbacks(
    favorites: LightFavorites,
    viewModel: MoreInfoLightViewModel,
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
        onMove = { from, to -> viewModel.save(colors.toMutableList().also { it.add(to, it.removeAt(from)) }) },
        onAdd = { open(FavoriteDialog.Add) },
        onStartEditing = { viewModel.setEditMode(true) },
        onDone = { viewModel.setEditMode(false) },
    )
}

@Composable
private fun FavoritesErrorText(error: FavoritesError) {
    val context = LocalContext.current
    val reason = context.loadErrorText(error.error)
    Text(
        text = when (error) {
            is FavoritesError.Save -> stringResource(R.string.native_dashboard_favorites_save_failed, reason)
            is FavoritesError.Copy -> stringResource(
                R.string.native_dashboard_favorites_copy_failed,
                error.failed,
                reason,
            )
        },
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorOnDangerQuiet,
    )
}

/**
 * The header's menu of a light's favourites, for admins: start or stop editing them, reset them to the defaults
 * (when some were saved) and copy them to other lights, as the more-info dialog's menu offers. Hidden until the
 * light's registry entry is loaded, and when it has none.
 */
@Composable
internal fun LightFavoritesMenu(state: EntityState, hass: HassSnapshot) {
    val viewModel = lightViewModel(state.entityId)
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    // Shown whatever the saved favourites, as edit mode would show them
    val favorites = remember(state, entry) {
        (entry as? Loadable.Ready)?.value?.let { hass.lightFavorites(state, it, editMode = true) }
    } ?: return
    if (hass.user?.isAdmin != true) return
    var expanded by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    val strings = "ui.dialogs.more_info_control.light"
    val moreOptions = stringResource(R.string.native_dashboard_more_options)
    Box {
        IconButton(onClick = { expanded = true }) {
            DashboardIcon(
                "mdi:dots-vertical",
                LocalHAColorScheme.current.colorTextPrimary,
                Modifier.size(HASize.X2L).semantics { contentDescription = moreOptions },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(
                if (editMode) "mdi:pencil-off" else "mdi:pencil",
                hass.localize(if (editMode) "ui.dialogs.more_info_control.exit_edit_mode" else "$strings.edit_mode"),
            ) {
                expanded = false
                viewModel.setEditMode(!editMode)
            }
            MenuItem("mdi:backup-restore", hass.localize("$strings.reset_favorites"), enabled = favorites.custom) {
                expanded = false
                confirmReset = true
            }
            MenuItem("mdi:content-duplicate", hass.localize("$strings.copy_favorites")) {
                expanded = false
                copying = true
            }
        }
    }
    FavoritesMenuDialogs(
        state = state,
        hass = hass,
        favorites = favorites,
        viewModel = viewModel,
        confirmReset = confirmReset,
        copying = copying,
        close = {
            confirmReset = false
            copying = false
        },
    )
}

/** The menu's reset confirmation and copy dialog, when open. */
@Composable
private fun FavoritesMenuDialogs(
    state: EntityState,
    hass: HassSnapshot,
    favorites: LightFavorites,
    viewModel: MoreInfoLightViewModel,
    confirmReset: Boolean,
    copying: Boolean,
    close: () -> Unit,
) {
    val strings = "ui.dialogs.more_info_control.light"
    if (confirmReset) {
        ConfirmDialog(
            title = hass.localize("$strings.reset_favorites"),
            text = hass.localize("$strings.reset_favorites_text"),
            confirm = stringResource(R.string.native_dashboard_reset),
            onConfirm = {
                close()
                viewModel.save(null)
            },
            onDismiss = { close() },
        )
    }
    if (copying) {
        val colors: List<LightColor> = favorites.favorites.map { it.color }
        CopyFavoritesDialog(
            hass = hass,
            targets = remember(hass, colors) { hass.favoriteCopyTargets(state.entityId, colors) },
            onCopy = { lights ->
                close()
                viewModel.copy(colors, lights)
            },
            onDismiss = { close() },
        )
    }
}

@Composable
private fun MenuItem(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = HATextStyle.Body,
                color = if (enabled) colors.colorTextPrimary else colors.colorTextDisabled,
            )
        },
        leadingIcon = {
            DashboardIcon(
                icon,
                if (enabled) colors.colorTextSecondary else colors.colorTextDisabled,
                Modifier.size(HASize.X2L),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/** The light's view model, shared by the favourites and the header's menu. */
@Composable
private fun lightViewModel(entityId: String): MoreInfoLightViewModel {
    val viewModel = hiltViewModel<MoreInfoLightViewModel>(key = "light-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    return viewModel
}

private const val FAVORITE_STRINGS = "ui.dialogs.more_info_control.light.favorite_color"
