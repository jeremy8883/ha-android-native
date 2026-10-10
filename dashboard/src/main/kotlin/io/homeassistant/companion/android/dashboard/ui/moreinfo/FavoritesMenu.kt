package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Box
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
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.FavoritesActions
import io.homeassistant.companion.android.dashboard.moreinfo.favoritesActions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.loadErrorText

/**
 * The header's favourites menu, for admins: start or stop editing the favourites of a light, cover or valve, reset
 * them to the defaults (when some were saved) and copy them to others, as the more-info dialog's menu offers.
 * Hidden until the entity's registry entry is loaded, and when it has none.
 */
@Composable
internal fun FavoritesMenu(state: EntityState, hass: HassSnapshot) {
    val viewModel = favoritesViewModel(state.entityId)
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val actions = remember(state, entry) {
        (entry as? Loadable.Ready)?.value?.let { hass.favoritesActions(state, it) }
    } ?: return
    if (hass.user?.isAdmin != true) return
    var expanded by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
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
                if (editMode) hass.localize("ui.dialogs.more_info_control.exit_edit_mode") else actions.editLabel,
            ) {
                expanded = false
                viewModel.setEditMode(!editMode)
            }
            MenuItem("mdi:backup-restore", actions.resetLabel, enabled = actions.custom) {
                expanded = false
                confirmReset = true
            }
            MenuItem("mdi:content-duplicate", actions.copyLabel) {
                expanded = false
                copying = true
            }
        }
    }
    FavoritesMenuDialogs(hass, actions, viewModel, confirmReset, copying) {
        confirmReset = false
        copying = false
    }
}

/** The menu's reset confirmation and copy dialog, when open. */
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

/** Why the last change of the favourites failed, under them. */
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

/** The entity's favourites view model, shared by its favourites and the header's menu. */
@Composable
internal fun favoritesViewModel(entityId: String): MoreInfoFavoritesViewModel {
    val viewModel = hiltViewModel<MoreInfoFavoritesViewModel>(key = "favorites-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    return viewModel
}
