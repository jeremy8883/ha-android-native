package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.EntityRowModel
import io.homeassistant.companion.android.dashboard.derive.RowControl
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import io.homeassistant.companion.android.dashboard.derive.RowNumberBox
import io.homeassistant.companion.android.dashboard.derive.RowSelect
import io.homeassistant.companion.android.dashboard.derive.RowSlider
import io.homeassistant.companion.android.dashboard.derive.RowTextInput
import io.homeassistant.companion.android.dashboard.derive.RowTimer
import io.homeassistant.companion.android.dashboard.derive.entitiesModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
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
            model.rows.forEach { row -> EntityRow(row, now.value?.toInstant() ?: Instant.EPOCH, interactions) }
        }
    }
}

@Composable
private fun EntityRow(row: EntityRowModel, now: Instant, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val textColor = if (row.available) colors.colorTextPrimary else colors.colorTextDisabled
    // Dropdowns and text fields carry the name as their label, in its place
    val namedControl = row.control is RowSelect || row.control is RowTextInput
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = HASize.X5L).padding(horizontal = HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon and name open more-info (or the row's tap action), like hui-generic-entity-row
        Row(
            modifier = (if (namedControl) Modifier else Modifier.weight(1f)).elementGestures(row.actions, interactions),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowBadge(row)
            if (!namedControl) {
                Text(
                    text = row.name,
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EntityRowControl(row, textColor, now, interactions)
    }
}

/** The control on the right of a row (or filling it, for one carrying the name), else its state. */
@Composable
private fun RowScope.EntityRowControl(
    row: EntityRowModel,
    textColor: Color,
    now: Instant,
    interactions: CardInteractions,
) {
    val colors = LocalHAColorScheme.current
    val onAction = interactions.onAction
    when (val control = row.control) {
        is RowControl.Toggle -> EntityToggle(
            checked = control.checked,
            updatedAt = control.updatedAt,
            onToggle = { onAction(control.action) },
            enabled = control.enabled,
        )
        is RowControl.Buttons -> control.buttons.forEach { button ->
            TextButton(onClick = { onAction(button.action) }, enabled = button.enabled) {
                Text(
                    button.label,
                    style = HATextStyle.BodyMedium,
                    color = if (button.danger) colors.colorOnDangerNormal else colors.colorOnPrimaryNormal,
                )
            }
        }
        is RowSlider -> Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) { RowSliderControl(control, onAction) }
        is RowNumberBox -> RowNumberBoxControl(control, onAction)
        is RowSelect -> RowSelectControl(control, onAction)
        is RowTextInput -> RowTextControl(control, onAction)
        is RowDateTime -> RowDateTimeControl(control, onAction)
        is RowTimer -> RowTimerText(control, now)
        null -> row.state?.let { Text(it, style = HATextStyle.BodyMedium, color = textColor, maxLines = 1) }
    }
}

/** Port of `state-badge` in a row: the entity's picture, else its icon in its badge colour; a warning when missing. */
@Composable
private fun RowBadge(row: EntityRowModel) {
    val colors = LocalHAColorScheme.current
    val badge = row.badge
    val picture = badge?.picture
    when {
        badge == null -> DashboardIcon(row.icon, colors.colorOnDangerNormal, Modifier.size(HASize.X2L))
        picture != null -> ServerImage(picture, row.name, Modifier.size(BADGE_SIZE).clip(CircleShape))
        else -> DashboardIcon(
            badge.icon,
            entityIconTint(badge.color, badge.unavailable, badge.brightness),
            Modifier.size(HASize.X2L),
        )
    }
}

/** `state-badge`'s 40 × 40. */
private val BADGE_SIZE = 40.dp
