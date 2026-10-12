package io.homeassistant.companion.android.dashboard.ui.components.targetpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.cssColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.target.TargetChip
import io.homeassistant.companion.android.dashboard.target.TargetPickerSource
import io.homeassistant.companion.android.dashboard.target.TargetType
import io.homeassistant.companion.android.dashboard.target.chips
import io.homeassistant.companion.android.dashboard.target.expand
import io.homeassistant.companion.android.dashboard.theme.resolveDisplayColor
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * Port of a compact `ha-target-picker` with `add-on-top` (frontend@20260624.6 src/components/ha-target-picker.ts):
 * "Add target", opening the sheet of entities, devices, areas (with floors) and labels to pick from, above a chip
 * for each picked item, which removes it or, but for entities, expands it into its parts. Only what holds an
 * entity passing [entityFilter] is offered. Unless [enabled] (`disabled`), nothing can be added or removed.
 *
 * Upstream: `ha-target-picker` (src/components/ha-target-picker.ts)
 */
@Composable
internal fun TargetPicker(
    target: Target,
    hass: HassSnapshot,
    entityFilter: (String) -> Boolean,
    onChange: (Target) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val viewModel = hiltViewModel<TargetPickerViewModel>()
    val configEntryDomains by viewModel.configEntryDomains.collectAsStateWithLifecycle()
    val source = remember(hass, configEntryDomains) { TargetPickerSource(hass, entityFilter, configEntryDomains) }
    val chips = remember(source, target) { source.chips(target) }
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        HAFilledButton(
            hass.localize("$TARGET.add_target"),
            { open = true },
            enabled = enabled,
            prefix = { DashboardIcon("mdi:playlist-plus", colors.colorOnPrimaryNormal, Modifier.size(HASize.X2L)) },
        )
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
                verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
            ) {
                chips.forEach { chip ->
                    TargetChipView(
                        chip = chip,
                        hass = hass,
                        enabled = enabled,
                        onRemove = { onChange(target - chip.ref) },
                        onExpand = { onChange(source.expand(target, chip.ref)) },
                    )
                }
            }
        }
    }
    if (open) {
        TargetPickerSheet(source, target, onDismiss = { open = false }) { ref ->
            open = false
            onChange(target + ref)
        }
    }
}

/**
 * Port of `ha-target-picker-value-chip`: a pill bordered in its type's colour (a label's own), its icon on that
 * colour, its name, the expand button but for entities, and the remove button.
 *
 * Upstream: `ha-target-picker-value-chip` (src/components/target-picker/ha-target-picker-value-chip.ts)
 */
@Composable
private fun TargetChipView(
    chip: TargetChip,
    hass: HassSnapshot,
    enabled: Boolean,
    onRemove: () -> Unit,
    onExpand: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val accent = chipAccent(chip, dark)
    val surface = resolveVariable("card-background-color", dark) ?: colors.colorSurfaceDefault
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(CHIP_BORDER, accent ?: colors.colorBorderNeutralQuiet, CircleShape)
            .background(surface)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // A floor's has no colour of its own
            Modifier.fillMaxHeight().background(
                accent ?: surface,
            ).padding(horizontal = Css.Space.S1, vertical = Css.Space.S2),
            contentAlignment = Alignment.Center,
        ) {
            TargetIconView(chip.icon, hass, Modifier.size(HASize.X2L), tint = colors.colorTextPrimary)
        }
        Text(
            chip.name,
            style = Css.Text.Body.copy(textAlign = TextAlign.Start),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Css.Space.S2).weight(1f, fill = false),
        )
        if (chip.expandable) {
            val label = hass.localize("$TARGET.expand_${chip.ref.type.key}_id")
            IconButton(
                onClick = onExpand,
                enabled = enabled,
                modifier = Modifier.size(EXPAND_SIZE).semantics {
                    contentDescription = label
                },
            ) {
                DashboardIcon("mdi:unfold-more-vertical", colors.colorTextPrimary, Modifier.size(EXPAND_ICON_SIZE))
            }
        }
        val removeLabel = hass.localize("$TARGET.remove_${chip.ref.type.key}_id")
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.size(REMOVE_SIZE).semantics {
                contentDescription =
                    removeLabel
            },
        ) {
            DashboardIcon("mdi:close", colors.colorTextPrimary, Modifier.size(HASize.L))
        }
    }
}

/** The chip's colour: green for entities, blue for devices, orange for areas, a label's own; floors have none. */
private fun chipAccent(chip: TargetChip, dark: Boolean): Color? = when (chip.ref.type) {
    TargetType.ENTITY -> resolveVariable("ha-color-green-80", dark)
    TargetType.DEVICE -> resolveVariable("ha-color-primary-80", dark)
    TargetType.AREA -> resolveVariable("ha-color-orange-80", dark)
    TargetType.LABEL -> chip.color?.let { cssColor(it).toColorOrNull(dark) }
    TargetType.FLOOR -> null
}

private fun DisplayColor.toColorOrNull(dark: Boolean): Color? = resolveDisplayColor(this, dark)?.let(::Color)

internal const val TARGET = "ui.components.target-picker"
private val CHIP_BORDER = 2.dp
private val EXPAND_SIZE = 28.dp
private val EXPAND_ICON_SIZE = 14.dp
private val REMOVE_SIZE = 36.dp
