package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.FavoritesActions
import io.homeassistant.companion.android.dashboard.moreinfo.favoritesActions
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.moreinfo.components.ConfirmDialog
import io.homeassistant.companion.android.dashboard.ui.moreinfo.components.CopyFavoritesDialog
import io.homeassistant.companion.android.dashboard.ui.theme.FrontendTextStyle

/** The favourites' items of the dialog's menu, each closing the menu with the given function first. */
internal typealias FavoritesMenuItems = @Composable (close: () -> Unit) -> Unit

/**
 * The favourites part of the dialog's menu, for admins: start or stop editing the favourites of a light, cover or
 * valve, reset them to the defaults (when some were saved) and copy them to others. [content] gets the items, or
 * `null` until the entity's registry entry is loaded and when it has none; their dialogs show from here.
 *
 * Upstream: part of `ha-more-info-dialog` (its favourites menu items), with `getFavoritesDialogHandler`
 * (src/dialogs/more-info/ha-more-info-dialog.ts; src/dialogs/more-info/favorites.ts)
 */
@Composable
internal fun FavoritesMenuHost(
    state: EntityState,
    hass: HassSnapshot,
    content: @Composable (FavoritesMenuItems?) -> Unit,
) {
    val viewModel = favoritesViewModel(state.entityId)
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val actions = remember(state, entry) {
        (entry as? Loadable.Ready)?.value?.let { hass.favoritesActions(state, it) }
    }
    if (actions == null || hass.user?.isAdmin != true) {
        content(null)
        return
    }
    var confirmReset by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    content { close ->
        MenuItem(
            if (editMode) "mdi:pencil-off" else "mdi:pencil",
            if (editMode) hass.localize("ui.dialogs.more_info_control.exit_edit_mode") else actions.editLabel,
        ) {
            close()
            viewModel.setEditMode(!editMode)
        }
        MenuItem("mdi:backup-restore", actions.resetLabel, enabled = actions.custom) {
            close()
            confirmReset = true
        }
        MenuItem("mdi:content-duplicate", actions.copyLabel) {
            close()
            copying = true
        }
    }
    FavoritesMenuDialogs(hass, actions, viewModel, confirmReset, copying) {
        confirmReset = false
        copying = false
    }
}

/**
 * The menu's reset confirmation and copy dialog, when open.
 *
 * Upstream: part of `ha-more-info-dialog` (`_resetFavorites`, `_copyFavorites`)
 * (src/dialogs/more-info/ha-more-info-dialog.ts; src/dialogs/more-info/favorites.ts)
 */
@Composable
private fun FavoritesMenuDialogs(
    hass: HassSnapshot,
    actions: FavoritesActions,
    viewModel: MoreInfoFavoritesViewModel,
    confirmReset: Boolean,
    copying: Boolean,
    close: () -> Unit,
) {
    if (confirmReset) {
        ConfirmDialog(
            title = actions.resetLabel,
            text = actions.resetText,
            confirm = stringResource(R.string.native_dashboard_reset),
            onConfirm = {
                close()
                viewModel.save(actions.reset)
            },
            onDismiss = close,
        )
    }
    if (copying) {
        CopyFavoritesDialog(
            hass = hass,
            title = actions.copyLabel,
            helper = actions.copyHelper,
            targets = actions.copyTargets,
            onCopy = { targets ->
                close()
                viewModel.copy(actions.copy(targets))
            },
            onDismiss = close,
        )
    }
}

/**
 * An item of the dialog's menu: its icon and label, greyed while disabled.
 *
 * Upstream: `ha-dropdown-item` (src/components/ha-dropdown-item.ts)
 */
@Composable
private fun MenuItem(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = FrontendTextStyle.Body,
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

/**
 * Why the last change of the favourites failed, under them.
 *
 * Upstream: none (native: the error of a failed favourites save or copy)
 */
@Composable
internal fun FavoritesErrorText(error: FavoritesError) {
    val reason = LocalContext.current.loadErrorText(error.error)
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
 * The entity's favourites view model, shared by its favourites and the header's menu.
 *
 * Upstream: none (native: view model lookup)
 */
@Composable
internal fun favoritesViewModel(entityId: String): MoreInfoFavoritesViewModel {
    val viewModel = hiltViewModel<MoreInfoFavoritesViewModel>(key = "favorites-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    return viewModel
}
