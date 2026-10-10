package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.EntityCardModel
import io.homeassistant.companion.android.dashboard.derive.entityCardModel
import io.homeassistant.companion.android.dashboard.display.ValueParts
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.ZonedDateTime

/**
 * An entity or sensor card, ports of `hui-entity-card` and `hui-sensor-card` (frontend@20260624.6
 * src/panels/lovelace/cards/): the entity's name with its icon on the right, its value large with the unit small
 * beside it, and for a sensor card with a graph, its history under them.
 */
@Composable
internal fun EntityCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val entity by remember(card) { derivedStateOf { hass.value?.entityCardModel(card) } }
    val model = when (val shown = entity) {
        null -> return
        is EntityCardModel.Warning -> return CardWarning(shown.text, modifier)
        is EntityCardModel.Shown -> shown
    }
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier.elementGestures(model.actions, interactions)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = HADimens.SPACE4,
                    top = HADimens.SPACE2,
                    end = HADimens.SPACE4,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    model.name,
                    style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = HADimens.SPACE2),
                )
                DashboardIcon(
                    model.icon,
                    model.color?.toColor() ?: colors.colorTextSecondary,
                    Modifier.size(iconHeight(model.iconHeight)),
                )
            }
            EntityValue(
                model.value,
                Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, bottom = HADimens.SPACE4),
            )
            model.graph?.let { SensorGraphView(it, hass, now, Modifier.fillMaxWidth()) }
        }
    }
}

/** The value large, its unit small beside it, in the locale's order. */
@Composable
private fun EntityValue(value: ValueParts, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
        val parts = listOfNotNull(
            value.value to true,
            value.unit?.let { it to false },
        ).let { if (value.unitFirst) it.reversed() else it }
        parts.forEach { (text, isValue) ->
            Text(
                text,
                style = if (isValue) {
                    HATextStyle.Headline.copy(fontSize = HAFontSize.X3L, fontWeight = FontWeight.Normal)
                } else {
                    HATextStyle.Body
                },
                color = if (isValue) colors.colorTextPrimary else colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** `icon_height` in pixels ("80px"), else the icon's usual size. */
private fun iconHeight(height: String?) = height?.removeSuffix("px")?.trim()?.toFloatOrNull()?.dp ?: ICON_SIZE

private val ICON_SIZE = 24.dp
