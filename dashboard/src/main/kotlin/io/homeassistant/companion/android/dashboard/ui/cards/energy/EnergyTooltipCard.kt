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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyTooltip

/** A tapped period's values, over the top of the chart, like the frontend's chart tooltip. */
@Composable
internal fun EnergyTooltipCard(tooltip: EnergyTooltip, dark: Boolean, formatTotal: (Double) -> String) {
    val colors = LocalHAColorScheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.colorSurfaceDefault),
        elevation = CardDefaults.cardElevation(defaultElevation = ELEVATION),
        shape = RoundedCornerShape(HARadius.M),
        modifier = Modifier.padding(start = HADimens.SPACE8, top = HADimens.SPACE2),
    ) {
        Column(Modifier.padding(HADimens.SPACE2), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
            Text(
                tooltip.title,
                style = HATextStyle.BodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Center,
            )
            tooltip.rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                ) {
                    val color = seriesColor(row.series, dark, background = false) ?: colors.colorTextSecondary
                    Box(Modifier.size(MARKER).background(color, CircleShape))
                    Text("${row.series.name}: ${row.value}", style = HATextStyle.Body, color = colors.colorTextPrimary)
                }
            }
            tooltip.total?.let {
                Text(
                    formatTotal(it),
                    style = HATextStyle.BodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.colorTextPrimary,
                )
            }
        }
    }
}

private val ELEVATION = 4.dp
private val MARKER = 10.dp
