package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.EntitiesModel
import io.homeassistant.companion.android.dashboard.derive.EntitiesRow
import io.homeassistant.companion.android.dashboard.derive.EntityRowModel
import io.homeassistant.companion.android.dashboard.derive.HeaderFooterModel
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
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.EntityToggle
import io.homeassistant.companion.android.dashboard.ui.components.HaSwitch
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.DomainRowState
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.MediaVolumeLine
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowDateTimeControl
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowName
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowNumberBoxControl
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowSelectControl
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowSliderArea
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowTextControl
import io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows.RowTimerText
import io.homeassistant.companion.android.dashboard.ui.lovelace.headerfooter.HeaderFooter
import io.homeassistant.companion.android.dashboard.ui.lovelace.specialrows.SpecialRow
import io.homeassistant.companion.android.dashboard.ui.theme.CardHeaderAboveContentPadding
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
import java.time.Instant
import java.time.ZonedDateTime

/**
 * An entities card: its header element, an optional title, one row per entity with its control or state, then its
 * footer element.
 *
 * Upstream: `hui-entities-card` (src/panels/lovelace/cards/hui-entities-card.ts)
 */
@Composable
internal fun EntitiesCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val context = LocalConditionContext.current
    val entities by remember(card, context) {
        derivedStateOf {
            val time = now.value
            hass.value?.entitiesModel(card, time?.toInstant() ?: Instant.EPOCH, context.copy(now = time))
        }
    }
    val model = entities ?: return
    DashboardCardSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            model.header?.let { header ->
                HeaderFooter(header, footer = false, hass, now, interactions)
                if (header !is HeaderFooterModel.Buttons) Spacer(Modifier.height(Css.Space.S4))
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = Css.Space.S4),
            ) {
                val hasTitle = model.title != null || model.headerToggle != null || model.icon != null
                if (hasTitle) EntitiesHeader(model, interactions)
                // `#states`: `.card-content` (no top padding under the header) with an 8px gap between rows
                Column(
                    modifier = Modifier.padding(top = if (hasTitle) 0.cssPx else Css.Space.S4),
                    verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
                ) {
                    model.rows.forEach { row ->
                        if (row is EntitiesRow.Entity) {
                            EntityRow(row.row, now.value?.toInstant() ?: Instant.EPOCH, interactions)
                        } else {
                            SpecialRow(row, interactions)
                        }
                    }
                }
            }
            model.footer?.let { HeaderFooter(it, footer = true, hass, now, interactions) }
        }
    }
}

/**
 * The card's header: its icon, title and the switch turning its entities on or off, each when it has one.
 *
 * Upstream: part of `hui-entities-card`, with `hui-entities-toggle` (src/panels/lovelace/cards/hui-entities-card.ts;
 * src/panels/lovelace/components/hui-entities-toggle.ts)
 */
@Composable
private fun EntitiesHeader(model: EntitiesModel, interactions: CardInteractions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(CardHeaderAboveContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `.icon`: `padding: 0 18px 0 8px`
        model.icon?.let {
            DashboardIcon(
                it,
                LocalHAColorScheme.current.colorTextSecondary,
                Modifier.padding(start = Css.Space.S2, end = HEADER_ICON_END).size(HASize.X2L),
            )
        }
        Text(model.title.orEmpty(), style = Css.Text.CardHeader, modifier = Modifier.weight(1f))
        model.headerToggle?.let { toggle ->
            HaSwitch(checked = toggle.checked, onCheckedChange = { on ->
                toggle.calls(on).forEach(interactions.onAction)
            })
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
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Css.Space.S4)) {
        // Number rows hide their state when at most 300px wide
        val wide = maxWidth > NARROW_ROW_WIDTH
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = HASize.X5L),
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
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
                    horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
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
                    EntityRowControl(row, textColor, now, interactions, wide)
                }
            }
            (row.control as? RowMedia)?.let { media ->
                media.volume?.let { MediaVolumeLine(media, it, interactions.onAction) }
            }
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
    wide: Boolean,
) = RowControlContent(row.control, row.state, textColor, now, interactions.onAction, wide)

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
    wide: Boolean = true,
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
        // The slider's `.flex` grows twice as much as the name (`flex-grow: 2`)
        is RowSlider -> RowSliderArea(control, onAction, showState = wide, Modifier.weight(SLIDER_AREA_WEIGHT))
        is RowNumberBox -> RowNumberBoxControl(control, onAction)
        is RowSelect -> RowSelectControl(control, onAction)
        is RowTextInput -> RowTextControl(control, onAction)
        is RowDateTime -> RowDateTimeControl(control, onAction)
        is RowTimer -> RowTimerText(control, now)
        null -> state?.let { Text(it, style = Css.Text.Body, color = textColor, maxLines = 1) }
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
            style = Css.Text.Body,
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

/** The width up to which `hui-input-number-entity-row` hides its state (`_measureCard`). */
private val NARROW_ROW_WIDTH = 300.dp

/** The header icon's `padding-right: 18px`. */
private val HEADER_ICON_END = 18.dp

/** A number row's slider and state grow twice as much as its name, as the frontend's `.flex { flex-grow: 2 }`. */
private const val SLIDER_AREA_WEIGHT = 2f
