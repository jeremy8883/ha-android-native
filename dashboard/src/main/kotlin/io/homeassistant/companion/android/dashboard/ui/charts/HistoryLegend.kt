package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.history.HistoryLegendItem
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * Port of `ha-chart-base`'s custom legend as history line charts show it: an item per line with a check (an empty
 * circle while hidden) that hides or shows it, its name (opening the entity's details) and the entity's value; past
 * [OVERFLOW_LIMIT] items a chip shows the rest.
 */
@Composable
internal fun HistoryLegend(
    items: List<HistoryLegendItem>,
    colors: Map<String, Color>,
    hidden: Set<String>,
    texts: HistoryLegendTexts,
    onToggle: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = LEGEND_TOP),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (expanded || index < OVERFLOW_LIMIT) {
                LegendItem(item, colors[item.id], item.id in hidden, texts.toggle, onToggle, onOpenEntity)
            }
        }
        if (items.size > OVERFLOW_LIMIT) {
            HAFilledButton(
                if (expanded) texts.less else "${texts.more} (${items.size - OVERFLOW_LIMIT})",
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

@Composable
private fun LegendItem(
    item: HistoryLegendItem,
    color: Color?,
    hidden: Boolean,
    toggleLabel: String,
    onToggle: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val style = TextStyle(fontSize = ChartText.FONT_SIZE)
    Row(
        modifier = Modifier.height(ITEM_HEIGHT).widthIn(
            max = if (item.value !=
                null
            ) {
                MAX_WIDTH_WITH_VALUE
            } else {
                MAX_WIDTH
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(
            if (hidden) "mdi:circle-outline" else "mdi:check-circle",
            if (hidden) colors.colorTextSecondary else color ?: colors.colorTextSecondary,
            Modifier.size(ICON_SIZE)
                .clickable(role = Role.Checkbox) { onToggle(item.id) }
                .semantics { contentDescription = toggleLabel },
        )
        Text(
            item.name,
            style = style,
            color = if (hidden) colors.colorTextSecondary else colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HADimens.SPACE1).weight(1f, fill = false)
                .clickable(role = Role.Button) { onOpenEntity(item.entityId) },
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

/** On a phone, the legend shows this many items before its "More" chip (`LEGEND_OVERFLOW_LIMIT_MOBILE`). */
private const val OVERFLOW_LIMIT = 6
private val LEGEND_TOP = 12.dp
private val ITEM_HEIGHT = 24.dp
private val ICON_SIZE = 18.dp
private val MAX_WIDTH = 220.dp
private val MAX_WIDTH_WITH_VALUE = 300.dp
