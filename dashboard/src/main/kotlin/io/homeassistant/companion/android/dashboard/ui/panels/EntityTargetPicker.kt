package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Close
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAInputChip
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.rememberSearchFieldState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.panels.PickerEntity
import io.homeassistant.companion.android.dashboard.panels.matching
import io.homeassistant.companion.android.dashboard.panels.pickerEntities
import io.homeassistant.companion.android.dashboard.panels.pickerEntity
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * Port of a compact `ha-target-picker` (`add-on-top`) that picks entities: "Add target" above a chip for each
 * picked entity, which removes it. Only the entities that pass [include] are offered.
 */
@Composable
internal fun EntityTargetPicker(
    selected: List<String>,
    hass: HassSnapshot,
    include: (String) -> Boolean,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HAFilledButton(
            hass.localize("$TARGET.add_target"),
            { open = true },
            prefix = { DashboardIcon("mdi:playlist-plus", colors.colorOnPrimaryNormal, Modifier.size(HASize.X2L)) },
        )
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                selected.forEach { entityId ->
                    HAInputChip(
                        text = hass.pickerEntity(entityId).name,
                        onClick = { onChange(selected - entityId) },
                        trailingIcon = Mdi.Close.rememberImageVector(),
                        trailingIconContentDescription = hass.localize("$TARGET.remove_entity_id"),
                    )
                }
            }
        }
    }
    if (open) {
        val entities = remember(hass.states.keys) { hass.pickerEntities(include) }
        EntityPickerDialog(entities.filter { it.entityId !in selected }, hass, onDismiss = { open = false }) {
            open = false
            onChange(selected + it)
        }
    }
}

/** The entities to pick from, found by name, place or id. */
@Composable
private fun EntityPickerDialog(
    entities: List<PickerEntity>,
    hass: HassSnapshot,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val search = rememberSearchFieldState()
    val shown = remember(entities, search.query) { entities.matching(search.query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { HAPlainButton(hass.localize("ui.common.cancel"), onDismiss) },
        title = { Text(hass.localize("$TARGET.add_target"), style = HATextStyle.HeadlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                HASearchField(state = search, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
                    items(shown, key = {
                        it.entityId
                    }) { entity -> EntityItem(entity, hass) { onPick(entity.entityId) } }
                }
            }
        },
    )
}

/** An entity to pick: its icon, its name and where it is. */
@Composable
private fun EntityItem(entity: PickerEntity, hass: HassSnapshot, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        DashboardIcon(hass.entityIcon(entity.entityId), colors.colorTextSecondary, Modifier.size(HASize.X2L))
        Column(Modifier.weight(1f)) {
            Text(
                entity.name,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entity.context ?: entity.entityId,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TARGET = "ui.components.target-picker"
private val LIST_MAX_HEIGHT = 400.dp
