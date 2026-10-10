package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.PositionFavorite
import io.homeassistant.companion.android.dashboard.moreinfo.PositionFavoriteSection
import io.homeassistant.companion.android.dashboard.moreinfo.PositionKind
import io.homeassistant.companion.android.dashboard.moreinfo.positionFavorites
import io.homeassistant.companion.android.dashboard.moreinfo.positionFavoritesUpdate
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/** Which favourite position dialog is open: [index] `null` adds one. */
private data class PositionDialog(val kind: PositionKind, val index: Int?, val delete: Boolean = false)

/**
 * A cover's or valve's favourite positions, port of `ha-more-info-cover-favorite-positions` and
 * `-valve-favorite-positions`: pills that set the position (the current one filled), and, while editing, added,
 * edited, deleted and moved, each change saved to the registry entry. Hidden until the entry is loaded.
 */
@Composable
internal fun PositionFavoritesSection(state: EntityState, hass: HassSnapshot, onAction: (CardAction) -> Unit) {
    val viewModel = favoritesViewModel(state.entityId)
    val loaded by viewModel.entry.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val entry = (loaded as? Loadable.Ready)?.value ?: return
    val favorites = remember(state, entry, editMode) { hass.positionFavorites(state, entry, editMode) } ?: return
    var dialog by remember { mutableStateOf<PositionDialog?>(null) }
    val active = DisplayColor.State(listOf("state-${state.domain}-active-color", "state-active-color")).toColor()
    val save = { kind: PositionKind, values: List<Double> ->
        viewModel.save(positionFavoritesUpdate(state, entry, kind, values))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        favorites.sections.forEach { section ->
            section.label?.let {
                Text(it, style = HATextStyle.BodyMedium, color = LocalHAColorScheme.current.colorTextSecondary)
            }
            FavoritesRow(
                deleteLabels = section.favorites.map { it.deleteLabel },
                state = FavoritesRowState(
                    enabled = favorites.enabled,
                    editMode = editMode,
                    isAdmin = hass.user?.isAdmin == true,
                    addLabel = section.addLabel,
                    doneLabel = favorites.doneLabel.takeIf { section.showDone },
                    maxWidth = ROW_WIDTH,
                ),
                callbacks = FavoritesRowCallbacks(
                    onApply = { onAction(section.favorites[it].apply) },
                    onEdit = { dialog = PositionDialog(section.kind, it) },
                    onDelete = { dialog = PositionDialog(section.kind, it, delete = true) },
                    onMove = { from, to ->
                        save(section.kind, section.values.toMutableList().also { it.add(to, it.removeAt(from)) })
                    },
                    onAdd = { dialog = PositionDialog(section.kind, null) },
                    onStartEditing = { viewModel.setEditMode(true) },
                    onDone = { viewModel.setEditMode(false) },
                ),
            ) { index, modifier -> PositionPill(section.favorites[index], favorites.enabled, active, modifier) }
        }
        error?.let { FavoritesErrorText(it) }
    }
    dialog?.let { open ->
        favorites.sections.firstOrNull { it.kind == open.kind }?.let { section ->
            PositionDialogs(open, section, state, hass, save) { dialog = null }
        }
    }
}

/** A favourite as a pill with its percentage, filled when the entity is there now. */
@Composable
private fun PositionPill(favorite: PositionFavorite, enabled: Boolean, active: Color?, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val filled = favorite.active && active != null
    Box(
        modifier = modifier
            .size(PILL_WIDTH, PILL_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(
                if (filled &&
                    active != null
                ) {
                    active
                } else {
                    colors.colorFillNeutralNormalResting
                },
                RoundedCornerShape(HARadius.Pill),
            )
            .semantics { contentDescription = favorite.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            favorite.text,
            style = HATextStyle.Body,
            color = if (filled) colors.colorOnPrimaryLoud else colors.colorOnNeutralNormal,
        )
    }
}

/** The add and edit prompt, or the delete confirmation, of one favourite; each change is saved. */
@Composable
private fun PositionDialogs(
    open: PositionDialog,
    section: PositionFavoriteSection,
    state: EntityState,
    hass: HassSnapshot,
    save: (PositionKind, List<Double>) -> Unit,
    close: () -> Unit,
) {
    val strings = "ui.dialogs.more_info_control.${state.domain}." +
        if (open.kind == PositionKind.Tilt) "favorite_tilt_position" else "favorite_position"
    val values = section.values
    if (open.delete && open.index != null) {
        ConfirmDialog(
            title = hass.localize("$strings.delete_confirm_title"),
            text = hass.localize("$strings.delete_confirm_text"),
            confirm = hass.localize("$strings.delete_confirm_action"),
            onConfirm = {
                close()
                save(open.kind, values.filterIndexed { i, _ -> i != open.index })
            },
            onDismiss = close,
        )
        return
    }
    PositionPrompt(
        title = hass.localize("$strings.${if (open.index == null) "add_title" else "edit_title"}"),
        label = hass.localize(
            if (open.kind ==
                PositionKind.Tilt
            ) {
                "ui.card.cover.tilt_position"
            } else {
                "ui.card.cover.position"
            },
        ),
        // The favourite as upstream writes it, without its "%"
        initial = open.index?.let { section.favorites.getOrNull(it)?.text?.removeSuffix("%") },
        onSave = { value ->
            close()
            save(
                open.kind,
                open.index?.let { index -> values.toMutableList().also { it[index] = value } } ?: (values + value),
            )
        },
        onDismiss = close,
    )
}

/** Port of the favourites' `showPromptDialog`: a percentage from 0 to 100, kept within when typed beyond. */
@Composable
private fun PositionPrompt(
    title: String,
    label: String,
    initial: String?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.orEmpty()) }
    val number = text.trim().toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = HATextStyle.HeadlineMedium) },
        text = {
            HATextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                trailingIcon = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            HAPlainButton(
                stringResource(commonR.string.save),
                {
                    number?.let { onSave(it.coerceIn(0.0, MAX)) }
                },
                enabled =
                number != null,
            )
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), onDismiss) },
    )
}

private const val DISABLED_ALPHA = 0.5f
private const val MAX = 100.0
private val PILL_WIDTH = 72.dp
private val PILL_HEIGHT = 36.dp
private val ROW_WIDTH = 384.dp
