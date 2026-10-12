package io.homeassistant.companion.android.dashboard.ui.components.targetpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.composable.rememberSearchFieldState
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.stateBadge
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.target.TARGET_SECTIONS
import io.homeassistant.companion.android.dashboard.target.Target
import io.homeassistant.companion.android.dashboard.target.TargetIcon
import io.homeassistant.companion.android.dashboard.target.TargetPickerItem
import io.homeassistant.companion.android.dashboard.target.TargetPickerItems
import io.homeassistant.companion.android.dashboard.target.TargetPickerRow
import io.homeassistant.companion.android.dashboard.target.TargetPickerSource
import io.homeassistant.companion.android.dashboard.target.TargetRef
import io.homeassistant.companion.android.dashboard.target.TargetType
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * Port of the target picker's sheet (`ha-generic-picker` with `ha-picker-combo-box` on a phone): the search, the
 * section chips (Entities, Devices, Areas, then Labels), and the list: each section's items under its title, or
 * one section's, the best matches first when searching.
 *
 * Upstream: `ha-generic-picker`, `ha-picker-combo-box` (src/components/ha-generic-picker.ts;
 * src/components/ha-picker-combo-box.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetPickerSheet(
    source: TargetPickerSource,
    target: Target,
    onDismiss: () -> Unit,
    onPick: (TargetRef) -> Unit,
) {
    val search = rememberSearchFieldState()
    var section by remember { mutableStateOf<TargetType?>(null) }
    val items = remember(source, target) { TargetPickerItems(source, target) }
    val rows = remember(items, search.query, section) { items.rows(search.query.trim(), section) }
    val colors = LocalHAColorScheme.current
    HAModalBottomSheet(
        bottomSheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        HASearchField(
            state = search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Css.Space.S4),
            leadingIcon = { DashboardIcon("mdi:magnify", colors.colorTextPrimary, Modifier.size(HASize.X2L)) },
        )
        SectionChips(source.hass, section) { section = if (section == it) null else it }
        LazyColumn(Modifier.fillMaxWidth().consumeSheetScrollFling()) {
            items(rows, key = ::rowKey) { row ->
                when (row) {
                    is TargetPickerRow.Title -> SectionTitle(row.text)
                    is TargetPickerRow.Item -> ItemRow(row.item, source.hass) { onPick(row.item.ref) }
                    is TargetPickerRow.Empty -> EmptyRow(row.text, searching = search.query.isNotBlank())
                }
            }
        }
    }
}

private fun rowKey(row: TargetPickerRow): String = when (row) {
    is TargetPickerRow.Title -> "title-${row.text}"
    is TargetPickerRow.Item -> "${row.item.ref.type.key}-${row.item.ref.id}"
    is TargetPickerRow.Empty -> "empty"
}

/**
 * The section chips: the chosen one filled, a separator before Labels; a tap chooses a section or shows all.
 *
 * Upstream: part of `ha-picker-combo-box` (src/components/ha-picker-combo-box.ts)
 */
@Composable
private fun SectionChips(hass: HassSnapshot, selected: TargetType?, onToggle: (TargetType) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = Css.Space.S4, end = Css.Space.S4, top = Css.Space.S3, bottom = Css.Space.S3),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TARGET_SECTIONS.forEach { type ->
            if (type == TargetType.LABEL) {
                Box(
                    Modifier.width(
                        SEPARATOR_WIDTH,
                    ).heightIn(min = Css.Space.S8).background(colors.colorBorderNeutralQuiet),
                )
            }
            val chosen = type == selected
            val shape = RoundedCornerShape(Css.Space.S2)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (chosen) {
                            Modifier.background(
                                resolveVariable("ha-color-fill-primary-normal-hover", isSystemInDarkTheme())
                                    ?: colors.colorFillPrimaryQuietResting,
                            )
                        } else {
                            Modifier.border(SECTION_BORDER, colors.colorBorderNeutralNormal, shape)
                        },
                    )
                    .clickable(role = Role.Checkbox) { onToggle(type) }
                    .padding(horizontal = Css.Space.S4, vertical = Css.Space.S2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
            ) {
                // A chosen filter chip shows a check
                if (chosen) DashboardIcon("mdi:check", colors.colorTextPrimary, Modifier.size(HASize.L))
                Text(
                    hass.localize("$TARGET.type.${SECTION_LABELS.getValue(type)}"),
                    style = Css.Text.Body,
                    color = colors.colorTextPrimary,
                )
            }
        }
    }
}

/**
 * A section's title band (`.title`).
 *
 * Upstream: part of `ha-picker-combo-box` (src/components/ha-picker-combo-box.ts)
 */
@Composable
internal fun SectionTitle(text: String) {
    val colors = LocalHAColorScheme.current
    Text(
        text,
        style = Css.Text.Body.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Bold),
        color = colors.colorTextSecondary,
        modifier = Modifier.fillMaxWidth().background(colors.colorFillNeutralQuietResting)
            .heightIn(min = Css.Space.S6)
            .padding(horizontal = Css.Space.S4, vertical = Css.Space.S1),
    )
}

/**
 * Port of `_renderRow`: an area under its floor drawn on the floor's tree, the item's icon, its name over its
 * place, and its integration's name at the end.
 *
 * Upstream: part of `ha-target-picker` (src/components/ha-target-picker.ts)
 */
@Composable
private fun ItemRow(item: TargetPickerItem, hass: HassSnapshot, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = if (item.secondary != null) TWO_LINE_HEIGHT else ONE_LINE_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.underFloor) {
            TreeIndicator(item.last, Modifier.width(TREE_WIDTH).fillMaxHeight())
        } else {
            Box(Modifier.width(Css.Space.S4))
        }
        Box(Modifier.size(ICON_BOX), contentAlignment = Alignment.Center) {
            item.icon?.let { TargetIconView(it, hass, Modifier.size(HASize.X2L), tint = colors.colorTextSecondary) }
        }
        Column(Modifier.weight(1f).padding(start = Css.Space.S4, top = Css.Space.S2, bottom = Css.Space.S2)) {
            Text(
                item.primary,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.secondary?.let {
                Text(
                    it,
                    style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.domainName?.let {
            Text(
                it,
                style = Css.Text.Body.copy(textAlign = TextAlign.End),
                color = colors.colorTextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = Css.Space.S2),
            )
        }
    }
}

/**
 * Port of `ha-tree-indicator`: a dashed line down the floor's areas, with a branch to this one.
 *
 * Upstream: `ha-tree-indicator` (src/components/ha-tree-indicator.ts)
 */
@Composable
private fun TreeIndicator(end: Boolean, modifier: Modifier) {
    val color = LocalHAColorScheme.current.colorBorderNeutralQuiet
    Canvas(modifier) {
        val unit = size.width / TREE_UNITS
        val stroke = TREE_STROKE.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke))
        val middle = size.height / 2
        drawLine(
            color,
            Offset(unit * TREE_X, 0f),
            Offset(unit * TREE_X, if (end) middle else size.height),
            stroke,
            pathEffect = dash,
        )
        drawLine(
            color,
            Offset(unit * TREE_X, middle),
            Offset(unit * TREE_BRANCH_END, middle),
            stroke,
            pathEffect = dash,
        )
    }
}

/**
 * That nothing matches the search, or nothing is offered.
 *
 * Upstream: part of `ha-picker-combo-box` (src/components/ha-picker-combo-box.ts)
 */
@Composable
internal fun EmptyRow(text: String, searching: Boolean) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = ONE_LINE_HEIGHT).padding(horizontal = Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
    ) {
        DashboardIcon(
            if (searching) "mdi:magnify" else "mdi:minus-box-outline",
            colors.colorTextSecondary,
            Modifier.size(HASize.X2L),
        )
        Text(text, style = Css.Text.Body.copy(textAlign = TextAlign.Start), color = colors.colorTextPrimary)
    }
}

/**
 * An item's or chip's icon: a named icon in [tint], an entity's state badge (in its state's colour for lights), or
 * an integration's logo.
 *
 * Upstream: part of `ha-target-picker`, `ha-target-picker-value-chip` (src/components/ha-target-picker.ts;
 * src/components/target-picker/ha-target-picker-value-chip.ts)
 */
@Composable
internal fun TargetIconView(icon: TargetIcon, hass: HassSnapshot, modifier: Modifier, tint: Color) {
    when (icon) {
        is TargetIcon.Named -> DashboardIcon(icon.icon, tint, modifier)
        is TargetIcon.Entity -> {
            val badge = remember(hass, icon.entityId) {
                hass.states[icon.entityId]?.let { hass.stateBadge(it, overrideIcon = null, stateColor = null) }
            }
            val picture = badge?.picture
            if (badge == null) {
                DashboardIcon("mdi:alert", tint, modifier)
            } else if (picture != null) {
                ServerImage(picture, contentDescription = null, modifier = modifier.clip(CircleShape))
            } else {
                // The picker's badges are in [tint] (`--state-icon-color` is the secondary text's there) but coloured ones
                val color = if (badge.color == null &&
                    !badge.unavailable
                ) {
                    tint
                } else {
                    entityIconTint(badge.color, badge.unavailable, badge.brightness)
                }
                DashboardIcon(badge.icon, color, modifier)
            }
        }
        is TargetIcon.Brand -> {
            val dark = if (isSystemInDarkTheme()) "dark_" else ""
            ServerImage(
                "/api/brands/integration/${icon.domain}/${dark}icon.png",
                contentDescription = null,
                modifier = modifier,
            )
        }
    }
}

private val SECTION_LABELS = mapOf(
    TargetType.ENTITY to "entities",
    TargetType.DEVICE to "devices",
    TargetType.AREA to "areas",
    TargetType.LABEL to "labels",
)
private val SEPARATOR_WIDTH = 2.dp
private val SECTION_BORDER = 1.dp
private val ONE_LINE_HEIGHT = 48.dp
private val TWO_LINE_HEIGHT = 64.dp
private val ICON_BOX = 32.dp

/** `ha-tree-indicator` draws on a 48 × 48 box: the line at 24, the branch to 36. */
private val TREE_WIDTH = 48.dp
private const val TREE_UNITS = 48f
private const val TREE_X = 24f
private const val TREE_BRANCH_END = 36f
private val TREE_STROKE = 2.dp
