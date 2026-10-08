package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.EntityRowModel
import io.homeassistant.companion.android.dashboard.derive.RowControl
import io.homeassistant.companion.android.dashboard.derive.entitiesModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.Instant
import java.time.ZonedDateTime

/** An entities card: an optional header, then one row per entity with its control or state. */
@Composable
internal fun EntitiesCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val entities by remember(card) {
        derivedStateOf { hass.value?.entitiesModel(card, now.value?.toInstant() ?: Instant.EPOCH) }
    }
    val model = entities ?: return
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE2)) {
            model.title?.let { title ->
                Row(
                    modifier = Modifier.padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardIcon(model.icon, colors.colorTextSecondary, Modifier.size(HASize.XL))
                    Text(title, style = HATextStyle.HeadlineMedium, color = colors.colorTextPrimary)
                }
            }
            model.rows.forEach { row -> EntityRow(row, interactions) }
        }
    }
}

@Composable
private fun EntityRow(row: EntityRowModel, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val textColor = if (row.available) colors.colorTextPrimary else colors.colorTextDisabled
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = HASize.X5L).padding(horizontal = HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon and name open more-info (or the row's tap action), like hui-generic-entity-row
        Row(
            modifier = Modifier.weight(1f).elementGestures(row.actions, interactions),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardIcon(
                name = row.icon,
                tint = if (row.missing) colors.colorOnDangerNormal else colors.colorTextSecondary,
                modifier = Modifier.size(HASize.X2L),
            )
            Text(
                text = row.name,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (val control = row.control) {
            is RowControl.Toggle -> Switch(
                checked = control.checked,
                onCheckedChange = { interactions.onAction(control.action) },
                enabled = control.enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.colorFillPrimaryLoudResting),
            )
            is RowControl.Buttons -> control.buttons.forEach { button ->
                TextButton(onClick = { interactions.onAction(button.action) }, enabled = button.enabled) {
                    Text(
                        button.label,
                        style = HATextStyle.BodyMedium,
                        color = if (button.danger) colors.colorOnDangerNormal else colors.colorOnPrimaryNormal,
                    )
                }
            }
            null -> row.state?.let { Text(it, style = HATextStyle.BodyMedium, color = textColor, maxLines = 1) }
        }
    }
}
