package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyTooltip
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/** A tapped period's values, over the top of the chart, like the frontend's chart tooltip. */
@Composable
internal fun EnergyTooltipCard(tooltip: EnergyTooltip, dark: Boolean, formatTotal: (Double) -> String) {
    val fallback = LocalHAColorScheme.current.colorTextSecondary
    ChartTooltipCard(
        title = tooltip.title,
        rows = tooltip.rows.map { row ->
            TooltipLine(
                seriesColor(row.series, dark, background = false) ?: fallback,
                "${row.series.name}: ${row.value}",
            )
        } + tooltip.lineRows.map { row ->
            TooltipLine(resolveVariable(row.line.color, dark) ?: fallback, "${row.line.name}: ${row.value}")
        },
        total = tooltip.total?.let(formatTotal),
    )
}

/** A line of a chart tooltip: a series' [color] marker and its [text]. */
internal class TooltipLine(val color: Color, val text: String)

/** A chart tooltip: its [title], a line per series and the [total], if any, in bold. */
@Composable
internal fun ChartTooltipCard(title: String, rows: List<TooltipLine>, total: String?) {
    val colors = LocalHAColorScheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.colorSurfaceDefault),
        elevation = CardDefaults.cardElevation(defaultElevation = ELEVATION),
        shape = RoundedCornerShape(HARadius.M),
        modifier = Modifier.padding(start = HADimens.SPACE8, top = HADimens.SPACE2),
    ) {
        Column(Modifier.padding(HADimens.SPACE2), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
            Text(
                title,
                style = HATextStyle.BodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Center,
            )
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                ) {
                    Box(Modifier.size(MARKER).background(row.color, CircleShape))
                    Text(row.text, style = HATextStyle.Body, color = colors.colorTextPrimary)
                }
            }
            total?.let {
                Text(it, style = HATextStyle.BodyMedium, fontWeight = FontWeight.Bold, color = colors.colorTextPrimary)
            }
        }
    }
}

private val ELEVATION = 4.dp
private val MARKER = 10.dp
