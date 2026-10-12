package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

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
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.EntityCardModel
import io.homeassistant.companion.android.dashboard.derive.entityCardModel
import io.homeassistant.companion.android.dashboard.display.ValueParts
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.SensorGraphView
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
import java.time.ZonedDateTime

/**
 * An entity or sensor card, ports of `hui-entity-card` and `hui-sensor-card` (frontend@20260624.6
 * src/panels/lovelace/cards/): the entity's name with its icon on the right, its value large with the unit small
 * beside it, and for a sensor card with a graph, its history under them.
 *
 * Upstream: `hui-entity-card`, `hui-sensor-card` (src/panels/lovelace/cards/hui-entity-card.ts;
 * src/panels/lovelace/cards/hui-sensor-card.ts)
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
                    start = Css.Space.S4,
                    top = Css.Space.S2,
                    end = Css.Space.S4,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    model.name,
                    style = Css.Text.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = Css.Space.S2),
                )
                DashboardIcon(
                    model.icon,
                    entityIconTint(model.color, brightness = model.brightness),
                    Modifier.size(iconHeight(model.iconHeight)),
                )
            }
            EntityValue(
                model.value,
                Modifier.padding(start = Css.Space.S4, end = Css.Space.S4, bottom = Css.Space.S4),
            )
            model.graph?.let { SensorGraphView(it, hass, now, Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * The value large, its unit small beside it, in the locale's order.
 *
 * Upstream: part of `hui-entity-card` (src/panels/lovelace/cards/hui-entity-card.ts)
 */
@Composable
private fun EntityValue(value: ValueParts, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Css.Space.S1)) {
        val parts = listOfNotNull(
            value.value to true,
            value.unit?.let { it to false },
        ).let { if (value.unitFirst) it.reversed() else it }
        parts.forEach { (text, isValue) ->
            Text(
                text,
                style = if (isValue) {
                    Css.Text.Primary.copy(fontSize = Css.FontSize.X3l, lineHeight = Css.LineHeight.Condensed)
                } else {
                    Css.Text.Body.copy(fontSize = Css.FontSize.L)
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
