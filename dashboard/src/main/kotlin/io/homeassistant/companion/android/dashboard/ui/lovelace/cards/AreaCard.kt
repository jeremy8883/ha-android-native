package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.AreaCardModel
import io.homeassistant.companion.android.dashboard.derive.AreaControlButton
import io.homeassistant.companion.android.dashboard.derive.AreaControls
import io.homeassistant.companion.android.dashboard.derive.AreaHeader
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.areaCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.CameraStreamView
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * An area card: a tile with the area's icon, name and sensors, under the area's picture (or a camera's snapshot or
 * stream, or its icon on the tile colour) with the active alerts, and its area controls. Compact, it is the tile
 * alone, the first alert a badge on its icon. A section's fixed rows give the picture the height left by the tile.
 *
 * Upstream: `hui-area-card` (src/panels/lovelace/cards/hui-area-card.ts)
 */
@Composable
internal fun AreaCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val area by remember(card) { derivedStateOf { hass.value?.areaCardModel(card) } }
    val model = when (val shown = area) {
        null -> return
        is AreaCardModel.Warning -> return CardWarning(shown.text, modifier)
        is AreaCardModel.Shown -> shown
    }
    val color = model.color.toColor() ?: LocalHAColorScheme.current.colorTextSecondary
    BoxWithConstraints(modifier) {
        // A section's fixed rows: upstream's grid layout ignores the aspect ratio and fills the cell
        val fixedHeight = if (constraints.minHeight > 0) minHeight else null
        DashboardCardSurface(Modifier.fillMaxWidth()) {
            // At least the rows' height, growing when the tile needs more like the grid's other cards
            Column(Modifier.fillMaxWidth().then(fixedHeight?.let { Modifier.heightIn(min = it) } ?: Modifier)) {
                model.header?.let { header ->
                    val sizing = if (fixedHeight != null) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.aspectRatio(header.aspectRatio.toFloat())
                    }
                    AreaPicture(header, model, color, hass, interactions, Modifier.fillMaxWidth().then(sizing))
                }
                AreaTile(model, color, minHeight = fixedHeight?.takeIf { model.header == null }, interactions)
            }
        }
    }
}

/**
 * The camera [entityId]'s stream, muted, covering the header.
 *
 * Upstream: part of `hui-image` (`camera_view: live`) (src/panels/lovelace/components/hui-image.ts)
 */
@Composable
private fun LiveCamera(entityId: String, hass: State<HassSnapshot?>) {
    val camera by remember(entityId) { derivedStateOf { hass.value?.states?.get(entityId) } }
    camera?.let { CameraStreamView(it, fill = true, muted = true, modifier = Modifier.fillMaxSize()) }
}

/**
 * The header: the picture (or the icon on the tile colour) with the alerts' chips over its top left.
 *
 * Upstream: part of `hui-area-card` (`.header`, `.alerts`) (src/panels/lovelace/cards/hui-area-card.ts)
 */
@Composable
private fun AreaPicture(
    header: AreaHeader,
    model: AreaCardModel.Shown,
    color: Color,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier,
) {
    val gestures = model.imageActions?.let { Modifier.elementGestures(it, interactions) } ?: Modifier
    Box(modifier.then(gestures)) {
        when (header) {
            is AreaHeader.Picture -> header.image?.let {
                ServerImage(it, contentDescription = model.name, modifier = Modifier.fillMaxSize())
            }
            is AreaHeader.LiveCamera -> LiveCamera(header.entityId, hass)
            is AreaHeader.Icon -> Box(
                Modifier.fillMaxSize().background(color.copy(alpha = HEADER_ICON_BACKGROUND_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                header.icon?.let { DashboardIcon(it, color, Modifier.size(Css.Space.S12)) }
            }
        }
        if (model.alerts.isNotEmpty()) {
            Row(Modifier.padding(Css.Space.S2), horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
                model.alerts.forEach { icon ->
                    Box(
                        Modifier
                            .size(Css.Space.S6)
                            .clip(RoundedCornerShape(Css.Radius.Xl))
                            .background(alertColor()),
                        contentAlignment = Alignment.Center,
                    ) {
                        DashboardIcon(icon, Color.White, Modifier.size(Css.Space.S4))
                    }
                }
            }
        }
    }
}

/**
 * The tile: icon (with the first alert as a badge when compact), name and sensors, side by side or stacked, and
 * the features under it or (inline) beside it.
 *
 * Upstream: `ha-tile-container` in `hui-area-card` (src/components/tile/ha-tile-container.ts;
 * src/panels/lovelace/cards/hui-area-card.ts)
 */
@Composable
private fun AreaTile(model: AreaCardModel.Shown, color: Color, minHeight: Dp?, interactions: CardInteractions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Alone in the card, it fills the rows' height (centred) but may grow
            .then(minHeight?.let { Modifier.heightIn(min = it) } ?: Modifier)
            .elementGestures(model.actions, interactions),
        verticalArrangement = Arrangement.Center,
    ) {
        if (model.vertical) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = TILE_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TILE_PADDING, Alignment.CenterVertically),
            ) {
                AreaTileIcon(model, color)
                AreaTileText(model, TextAlign.Center, Modifier.fillMaxWidth().padding(horizontal = Css.Space.S3))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = TILE_ROW_HEIGHT).padding(horizontal = TILE_PADDING),
                horizontalArrangement = Arrangement.spacedBy(TILE_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AreaTileIcon(model, color)
                AreaTileText(model, TextAlign.Start, Modifier.weight(1f))
                if (model.inlineFeatures) model.features.forEach { AreaControlsRow(it, interactions, stretch = false) }
            }
        }
        if (!model.inlineFeatures && model.features.isNotEmpty()) {
            Column(
                Modifier.padding(start = Css.Space.S3, end = Css.Space.S3, bottom = Css.Space.S3),
                // `hui-card-features`' gap
                verticalArrangement = Arrangement.spacedBy(Css.Space.S3),
            ) {
                model.features.forEach { AreaControlsRow(it, interactions, stretch = true) }
            }
        }
    }
}

/**
 * The area's icon on a circle of the tile colour, with the first alert's badge when compact.
 *
 * Upstream: `ha-tile-icon`, `ha-tile-badge` (src/components/tile/ha-tile-icon.ts; src/components/tile/ha-tile-badge.ts)
 */
@Composable
private fun AreaTileIcon(model: AreaCardModel.Shown, color: Color) {
    Box {
        // Not interactive, so without the tile icon's background
        Box(Modifier.size(TILE_ICON_SIZE), contentAlignment = Alignment.Center) {
            DashboardIcon(model.icon, color, Modifier.size(HASize.X2L))
        }
        // Compact cards carry a single alert, as a badge
        if (model.header == null) {
            model.alerts.firstOrNull()?.let { icon ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(alertColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    DashboardIcon(icon, Color.White, Modifier.size(BADGE_ICON_SIZE))
                }
            }
        }
    }
}

/**
 * The area's name with its sensors under it.
 *
 * Upstream: `ha-tile-info` (src/components/tile/ha-tile-info.ts)
 */
@Composable
private fun AreaTileText(model: AreaCardModel.Shown, textAlign: TextAlign, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Column(
        modifier = modifier,
        horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            model.name,
            style = TILE_PRIMARY_STYLE.copy(textAlign = textAlign),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        model.sensors?.let {
            Text(
                it,
                style = TILE_SECONDARY_STYLE.copy(textAlign = textAlign),
                // `ha-tile-info`'s secondary line is in the primary text colour
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The area controls: a row of buttons, filled in their colour while what they control is on; 48dp each inline.
 *
 * Upstream: `hui-area-controls-card-feature`, `ha-control-button`
 * (src/panels/lovelace/card-features/hui-area-controls-card-feature.ts; src/components/ha-control-button.ts)
 */
@Composable
private fun AreaControlsRow(controls: AreaControls, interactions: CardInteractions, stretch: Boolean) {
    Row(
        modifier = if (stretch) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
    ) {
        controls.buttons.forEach { button ->
            AreaControlButtonView(
                button,
                interactions,
                if (stretch) Modifier.weight(1f) else Modifier.size(INLINE_BUTTON_WIDTH, CONTROL_HEIGHT),
            )
        }
    }
}

/**
 * One area control.
 *
 * Upstream: part of `hui-area-controls-card-feature`, with `ha-control-button`
 * (src/panels/lovelace/card-features/hui-area-controls-card-feature.ts; src/components/ha-control-button.ts)
 */
@Composable
private fun AreaControlButtonView(button: AreaControlButton, interactions: CardInteractions, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val active = button.activeColor.toColor() ?: colors.colorFillPrimaryLoudResting
    val background = if (button.active) active else DisplayColor.Theme(DISABLED).toColor() ?: colors.colorTextDisabled
    Box(
        modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clip(RoundedCornerShape(Css.Radius.Xl))
            .background(background.copy(alpha = CONTROL_BACKGROUND_ALPHA))
            .clickable(role = Role.Button) { interactions.onAction(button.action) }
            .semantics { contentDescription = button.label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(button.icon, if (button.active) active else colors.colorTextPrimary, Modifier.size(HASize.XL))
    }
}

/**
 * `--orange-color`, the alerts' background.
 *
 * Upstream: part of `hui-area-card` (`.alert`, `.alert-badge`) (src/panels/lovelace/cards/hui-area-card.ts)
 */
@Composable
private fun alertColor(): Color = DisplayColor.Theme(ORANGE).toColor() ?: Color(ORANGE_FALLBACK)

private const val ORANGE = "orange"
private const val ORANGE_FALLBACK = 0xFFFF9800
private const val DISABLED = "disabled"

/** `.icon-container::before`: the tile colour at 12 % behind the header's icon. */
private const val HEADER_ICON_BACKGROUND_ALPHA = 0.12f

/** `--control-button-background-opacity`. */
private const val CONTROL_BACKGROUND_ALPHA = 0.2f
private val CONTROL_HEIGHT = Css.Space.S10
private val INLINE_BUTTON_WIDTH = 48.dp
private val BADGE_SIZE = 16.dp
private val BADGE_ICON_SIZE = 12.dp
private val BADGE_OFFSET: Dp = 3.dp
