package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
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
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

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
                    TargetChip(
                        name = hass.pickerEntity(entityId).name,
                        icon = hass.entityIcon(entityId),
                        removeLabel = hass.localize("$TARGET.remove_entity_id"),
                        onRemove = { onChange(selected - entityId) },
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

/**
 * Port of `ha-target-picker-value-chip` for an entity: a pill with a green border, the entity's icon on green, its
 * name and a remove button.
 */
@Composable
private fun TargetChip(name: String, icon: String?, removeLabel: String, onRemove: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val accent = resolveVariable(ENTITY_COLOR, dark) ?: colors.colorBorderSuccessNormal
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(CHIP_BORDER, accent, CircleShape)
            .background(resolveVariable("card-background-color", dark) ?: colors.colorSurfaceDefault)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().background(
                accent,
            ).padding(horizontal = HADimens.SPACE1, vertical = HADimens.SPACE2),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
        }
        Text(
            name,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HADimens.SPACE2).weight(1f, fill = false),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(CHIP_REMOVE_SIZE).semantics {
                contentDescription =
                    removeLabel
            },
        ) {
            DashboardIcon("mdi:close", colors.colorTextPrimary, Modifier.size(HASize.L))
        }
    }
}

/** The entities to pick from, found by name, place or id, in a sheet as upstream's picker on a phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityPickerDialog(
    entities: List<PickerEntity>,
    hass: HassSnapshot,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val search = rememberSearchFieldState()
    val shown = remember(entities, search.query) { entities.matching(search.query) }
    val colors = LocalHAColorScheme.current
    HAModalBottomSheet(
        bottomSheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        HASearchField(
            state = search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4),
            leadingIcon = { DashboardIcon("mdi:magnify", colors.colorTextPrimary, Modifier.size(HASize.X2L)) },
        )
        LazyColumn(Modifier.fillMaxWidth().padding(top = HADimens.SPACE3).consumeSheetScrollFling()) {
            item(key = "header") {
                Text(
                    hass.localize("$TARGET.type.entities"),
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Medium),
                    color = colors.colorTextSecondary,
                    modifier = Modifier.fillMaxWidth()
                        .background(colors.colorFillNeutralQuietResting)
                        .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
                )
            }
            items(shown, key = { it.entityId }) { entity -> EntityItem(entity, hass) { onPick(entity.entityId) } }
        }
    }
}

/** An entity to pick: its icon, its name over where it is, and its integration's name. */
@Composable
private fun EntityItem(entity: PickerEntity, hass: HassSnapshot, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(
            min = ITEM_MIN_HEIGHT,
        ).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
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
            entity.context?.let {
                Text(
                    it,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entity.domainName?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                color = colors.colorTextSecondary,
                maxLines = 1,
            )
        }
    }
}

private const val ENTITY_COLOR = "ha-color-green-80"
private val CHIP_BORDER = 2.dp
private val CHIP_REMOVE_SIZE = 36.dp
private const val TARGET = "ui.components.target-picker"
private val ITEM_MIN_HEIGHT = 56.dp
