package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.legendOverflowLimit
import io.homeassistant.companion.android.dashboard.history.legendSoloed
import io.homeassistant.companion.android.dashboard.history.legendToggled
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * An item of a chart's legend: the series it hides or shows ([id]), its [name], its [color] (the check's), its
 * [value] if any, and whether a tap on its name opens something ([opensLabel], `click-label-for-more-info` without
 * `noLabelClick`) rather than toggling it.
 */
internal class ChartLegendItem(
    val id: String,
    val name: String,
    val color: Color?,
    val value: String? = null,
    val opensLabel: Boolean = false,
)

/**
 * Port of `ha-chart-base`'s custom legend: an item per series with a check (an empty circle while hidden) that hides
 * or shows it, its name and its value; a tap on the name opens [onOpen] for items with [ChartLegendItem.opensLabel],
 * else toggles too, and a long press shows only that item (or all of them again). Past 6 items on a phone (10
 * otherwise) a chip shows the rest. [onHiddenChange] gets the new set of hidden ids.
 *
 * Upstream: part of `ha-chart-base` (src/components/chart/ha-chart-base.ts)
 */
@Composable
internal fun ChartLegend(
    items: List<ChartLegendItem>,
    hidden: Set<String>,
    texts: HistoryLegendTexts,
    onHiddenChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val limit = legendOverflowLimit(configuration.screenWidthDp, configuration.screenHeightDp)
    val actions = LegendActions(
        toggleLabel = texts.toggle,
        onToggle = { onHiddenChange(legendToggled(hidden, it)) },
        onSolo = { id -> onHiddenChange(legendSoloed(items.map { it.id }, hidden, id)) },
        onOpen = onOpen,
    )
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = LEGEND_TOP),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        // On a touch screen the rows are taller instead of spaced (`@media (pointer: coarse)`)
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (expanded || index < limit) {
                LegendItem(item, item.id in hidden, multiple = items.size > 1, actions)
            }
        }
        if (items.size > limit) {
            HAFilledButton(
                if (expanded) texts.less else "${texts.more} (${items.size - limit})",
                { expanded = !expanded },
                variant = ButtonVariant.NEUTRAL,
                size = ButtonSize.SMALL,
                suffix = {
                    DashboardIcon(
                        if (expanded) "mdi:chevron-up" else "mdi:chevron-down",
                        LocalHAColorScheme.current.colorTextPrimary,
                        Modifier.size(ICON_SIZE),
                    )
                },
            )
        }
    }
}

/** What the legend says: its toggle's label and its overflow chip's ("More", "Less"). */
internal data class HistoryLegendTexts(val toggle: String, val more: String, val less: String)

/** The legend's texts in [this] server's language: its toggle's label and its overflow chip's. */
internal fun HassSnapshot.legendTexts() = HistoryLegendTexts(
    toggle = localize("$CHARTS.toggle_visibility"),
    more = localize("$CHARTS.expand_legend"),
    less = localize("$CHARTS.collapse_legend"),
)

/** What the legend's items do: toggle, solo (a long press) or open an item, by id. */
private class LegendActions(
    val toggleLabel: String,
    val onToggle: (String) -> Unit,
    val onSolo: (String) -> Unit,
    val onOpen: (String) -> Unit,
)

/**
 * A legend item: its check that hides or shows the series, its name and its value; [multiple] when the legend has
 * more than one item, which caps its width.
 *
 * Upstream: part of `ha-chart-base` (src/components/chart/ha-chart-base.ts)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LegendItem(item: ChartLegendItem, hidden: Boolean, multiple: Boolean, actions: LegendActions) {
    val colors = LocalHAColorScheme.current
    val style = TextStyle(fontSize = ChartText.FONT_SIZE)
    val maxWidth = when {
        !multiple -> Dp.Unspecified
        item.value != null -> MAX_WIDTH_WITH_VALUE
        else -> MAX_WIDTH
    }
    Row(
        modifier = Modifier.height(ITEM_HEIGHT).widthIn(max = maxWidth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(
            if (hidden) "mdi:circle-outline" else "mdi:check-circle",
            if (hidden) colors.colorTextSecondary else item.color ?: colors.colorTextSecondary,
            Modifier
                .combinedClickable(
                    role = Role.Checkbox,
                    onLongClick = { actions.onSolo(item.id) },
                    onClick = { actions.onToggle(item.id) },
                )
                .semantics { contentDescription = actions.toggleLabel }
                .padding(start = TOGGLE_PADDING, top = TOGGLE_PADDING, bottom = TOGGLE_PADDING, end = HADimens.SPACE1)
                .size(ICON_SIZE),
        )
        Text(
            item.name,
            style = style,
            color = if (hidden) colors.colorTextSecondary else colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HADimens.SPACE1).weight(1f, fill = false)
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = { actions.onSolo(item.id) },
                    onClick = { if (item.opensLabel) actions.onOpen(item.id) else actions.onToggle(item.id) },
                ),
        )
        item.value?.let {
            Text(
                it,
                style = style,
                color = colors.colorTextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = HADimens.SPACE1),
            )
        }
    }
}

private const val CHARTS = "ui.components.history_charts"
private val LEGEND_TOP = 12.dp

/** An item's height on a touch screen, its check's padding making it easier to tap (`@media (pointer: coarse)`). */
private val ITEM_HEIGHT = 40.dp
private val TOGGLE_PADDING = 11.dp
private val ICON_SIZE = 18.dp
private val MAX_WIDTH = 220.dp
private val MAX_WIDTH_WITH_VALUE = 300.dp
