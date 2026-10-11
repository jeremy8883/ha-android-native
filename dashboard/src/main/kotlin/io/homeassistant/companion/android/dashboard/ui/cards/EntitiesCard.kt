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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.EntityRowModel
import io.homeassistant.companion.android.dashboard.derive.RowButton
import io.homeassistant.companion.android.dashboard.derive.RowControl
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import io.homeassistant.companion.android.dashboard.derive.RowIconButton
import io.homeassistant.companion.android.dashboard.derive.RowMedia
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

/**
 * An entities card: an optional header, then one row per entity with its control or state.
 *
 * Upstream: `hui-entities-card`, with `hui-entities-toggle` (src/panels/lovelace/cards/hui-entities-card.ts;
 * src/panels/lovelace/components/hui-entities-toggle.ts)
 */
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
            if (model.title != null || model.headerToggle != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = HADimens.SPACE4,
                        vertical = HADimens.SPACE2,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardIcon(model.icon, colors.colorTextSecondary, Modifier.size(HASize.XL))
                    Text(
                        model.title.orEmpty(),
                        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                        color = colors.colorTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // `hui-entities-toggle`
                    model.headerToggle?.let { toggle ->
                        HASwitch(checked = toggle.checked, onCheckedChange = { on ->
                            toggle.calls(on).forEach(interactions.onAction)
                        })
                    }
                }
            }
            model.rows.forEach { row -> EntityRow(row, now.value?.toInstant() ?: Instant.EPOCH, interactions) }
        }
    }
}

/**
 * One row of an entities card: the entity's badge and name opening its details, then its control or state.
 *
 * Upstream: `hui-generic-entity-row`, `createRowElement` (src/panels/lovelace/components/hui-generic-entity-row.ts;
 * src/panels/lovelace/create-element/create-row-element.ts)
 */
@Composable
private fun EntityRow(row: EntityRowModel, now: Instant, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val textColor = if (row.available) colors.colorTextPrimary else colors.colorTextDisabled
    // Dropdowns and text fields carry the name as their label, in its place
    val namedControl = row.control is RowSelect || row.control is RowTextInput
    val domain = row.domain
    Column(Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HASize.X5L),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icon and name open more-info (or the row's tap action), like hui-generic-entity-row
            Row(
                modifier = (
                    if (namedControl) {
                        Modifier
                    } else {
                        Modifier.weight(
                            1f,
                        )
                    }
                    ).elementGestures(row.actions, interactions),
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                domain?.condition?.let { WeatherIconView(it, colors.colorTextSecondary, Modifier.size(BADGE_SIZE)) }
                    ?: RowBadge(row)
                if (!namedControl) RowName(row.name, domain?.nameSecondary, textColor)
            }
            val twoLines = domain?.let { it.stateSecondary != null || it.weatherDetail != null } == true
            if (row.control == null && domain != null && twoLines) {
                DomainRowState(row.state, domain, textColor)
            } else {
                EntityRowControl(row, textColor, now, interactions)
            }
        }
        (row.control as? RowMedia)?.let { media ->
            media.volume?.let { MediaVolumeLine(media, it, interactions.onAction) }
        }
    }
}

/**
 * The control on the right of a row (or filling it, for one carrying the name), else its state.
 *
 * Upstream: the `hui-*-entity-row` elements (src/panels/lovelace/entity-rows/)
 */
@Composable
private fun RowScope.EntityRowControl(
    row: EntityRowModel,
    textColor: Color,
    now: Instant,
    interactions: CardInteractions,
) = RowControlContent(row.control, row.state, textColor, now, interactions.onAction)

/**
 * A row's [control] (a switch, buttons or an input), or its [state] text without one.
 *
 * Upstream: the `hui-*-entity-row` elements (src/panels/lovelace/entity-rows/)
 */
@Composable
internal fun RowScope.RowControlContent(
    control: RowControl?,
    state: String?,
    textColor: Color,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    when (control) {
        is RowControl.Toggle -> EntityToggle(
            checked = control.checked,
            updatedAt = control.updatedAt,
            onToggle = { onAction(control.action) },
            enabled = control.enabled,
        )
        is RowControl.Buttons -> control.buttons.forEach { button -> RowTextButton(button, onAction) }
        is RowControl.IconButtons, is RowMedia -> rowIconButtons(control).forEach { RowIconButtonView(it, onAction) }
        is RowSlider -> Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) { RowSliderControl(control, onAction) }
        is RowNumberBox -> RowNumberBoxControl(control, onAction)
        is RowSelect -> RowSelectControl(control, onAction)
        is RowTextInput -> RowTextControl(control, onAction)
        is RowDateTime -> RowDateTimeControl(control, onAction)
        is RowTimer -> RowTimerText(control, now)
        null -> state?.let { Text(it, style = HATextStyle.BodyMedium, color = textColor, maxLines = 1) }
    }
}

/** The icon buttons on a row: a cover's, or a media player's top ones (its volume line has the others). */
private fun rowIconButtons(control: RowControl): List<RowIconButton> = when (control) {
    is RowControl.IconButtons -> control.buttons
    is RowMedia -> control.top
    else -> emptyList()
}

/**
 * A text button of a row, a cancelling one in the danger colour.
 *
 * Upstream: `hui-button-entity-row`, `hui-input-button-entity-row`, `hui-lock-entity-row`, `hui-scene-entity-row`,
 * `hui-script-entity-row` (src/panels/lovelace/entity-rows/hui-button-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-input-button-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-lock-entity-row.ts; src/panels/lovelace/entity-rows/hui-scene-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-script-entity-row.ts)
 */
@Composable
private fun RowTextButton(button: RowButton, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    TextButton(onClick = { onAction(button.action) }, enabled = button.enabled) {
        Text(
            button.label,
            style = HATextStyle.BodyMedium,
            color = if (button.danger) colors.colorOnDangerNormal else colors.colorOnPrimaryNormal,
        )
    }
}

/**
 * An icon button of a row; an unsupported one keeps its place, invisible. The current state's is in the accent.
 *
 * Upstream: `ha-cover-controls`, part of `hui-media-player-entity-row` (src/components/ha-cover-controls.ts;
 * src/panels/lovelace/entity-rows/hui-media-player-entity-row.ts)
 */
@Composable
internal fun RowIconButtonView(button: RowIconButton, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    IconButton(
        onClick = { onAction(button.action) },
        enabled = button.enabled && button.shown,
        modifier = Modifier.alpha(if (button.shown) 1f else 0f).semantics { contentDescription = button.label },
    ) {
        DashboardIcon(
            button.icon,
            when {
                !button.enabled -> colors.colorTextDisabled
                button.active -> colors.colorOnPrimaryNormal
                else -> colors.colorTextPrimary
            },
            Modifier.size(HASize.X2L),
        )
    }
}

/**
 * Port of `state-badge` in a row: the entity's picture, else its icon in its badge colour; a warning when missing.
 *
 * Upstream: `state-badge` (src/components/entity/state-badge.ts)
 */
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
