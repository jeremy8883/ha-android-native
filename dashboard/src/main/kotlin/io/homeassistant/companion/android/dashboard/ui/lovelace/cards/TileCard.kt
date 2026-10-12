package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.TileBadge
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures.TileFeatureControl
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

/**
 * A tile card for an entity, or a placeholder when the entity doesn't exist.
 *
 * Upstream: `hui-tile-card` (src/panels/lovelace/cards/hui-tile-card.ts)
 */
@Composable
internal fun TileCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onAction: (CardAction) -> Unit = {},
) {
    val tile by remember(card) {
        derivedStateOf { hass.value?.tileModel(card, now.value?.toInstant() ?: Instant.EPOCH) }
    }
    val model = tile
    if (model == null) {
        UnsupportedCard(stringResource(R.string.native_dashboard_entity_not_found, card.entity.orEmpty()), modifier)
    } else {
        TileCardContent(model, modifier, iconModifier, onAction)
    }
}

/**
 * A tile: its icon, name and state, then its features.
 *
 * Upstream: part of `hui-tile-card`, with `ha-tile-container` (src/panels/lovelace/cards/hui-tile-card.ts;
 * src/components/tile/ha-tile-container.ts)
 */
@Composable
internal fun TileCardContent(
    tile: TileModel,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onAction: (CardAction) -> Unit = {},
) {
    DashboardCardSurface(modifier = modifier) {
        // `ha-tile-container`: the icon and text take the height left over, centred, the features at the bottom
        BoxWithConstraints {
            val bounded = constraints.hasBoundedHeight
            Column(modifier = Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier)) {
                Box(
                    modifier = if (bounded) Modifier.weight(1f) else Modifier,
                    contentAlignment = Alignment.CenterStart,
                ) { TileInfo(tile, iconModifier) }
                TileFeatures(tile, onAction)
            }
        }
    }
}

/**
 * The tile's features under its content, a gap apart.
 *
 * Upstream: `hui-card-features` (src/panels/lovelace/card-features/hui-card-features.ts)
 */
@Composable
private fun TileFeatures(tile: TileModel, onAction: (CardAction) -> Unit) {
    if (tile.features.isEmpty()) return
    Column(
        modifier = Modifier.padding(start = Css.Space.S3, end = Css.Space.S3, bottom = Css.Space.S3),
        // `hui-card-features`' gap
        verticalArrangement = Arrangement.spacedBy(Css.Space.S3),
    ) {
        val color = tile.color.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
        tile.features.forEach { feature -> TileFeatureControl(feature, tile.available, color, onAction) }
    }
}

/**
 * The tile's icon and text, side by side or (vertical) one above the other.
 *
 * Upstream: part of `ha-tile-container` (src/components/tile/ha-tile-container.ts)
 */
@Composable
private fun TileInfo(tile: TileModel, iconModifier: Modifier) {
    if (tile.vertical) {
        VerticalTileInfo(tile, iconModifier)
    } else {
        HorizontalTileInfo(tile, iconModifier)
    }
}

/**
 * The icon on the left of the name and state.
 *
 * Upstream: part of `ha-tile-container` (src/components/tile/ha-tile-container.ts)
 */
@Composable
private fun HorizontalTileInfo(tile: TileModel, iconModifier: Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TILE_ROW_HEIGHT).padding(horizontal = TILE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TILE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(tile, iconModifier)
        TileText(tile, TextAlign.Start, Modifier.weight(1f))
    }
}

/**
 * The icon above the name and state, centred.
 *
 * Upstream: part of `ha-tile-container` (src/components/tile/ha-tile-container.ts)
 */
@Composable
private fun VerticalTileInfo(tile: TileModel, iconModifier: Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = VERTICAL_TILE_MIN_HEIGHT).padding(vertical = TILE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_PADDING, Alignment.CenterVertically),
    ) {
        TileIcon(tile, iconModifier)
        TileText(tile, TextAlign.Center, Modifier.fillMaxWidth().padding(horizontal = Css.Space.S3))
    }
}

/**
 * Port of `ha-tile-icon`: the icon in the tile's colour, on a circle of it at a fifth of its strength when the icon
 * has its own action (`interactive`).
 *
 * Upstream: `ha-tile-icon` (src/components/tile/ha-tile-icon.ts)
 */
@Composable
private fun TileIcon(tile: TileModel, modifier: Modifier) {
    val color = tile.color.toColor() ?: LocalHAColorScheme.current.colorTextSecondary
    tile.icon?.let { icon ->
        val background = if (tile.iconInteractive) {
            Modifier.clip(CircleShape).background(color.copy(alpha = ICON_BACKGROUND_ALPHA))
        } else {
            Modifier
        }
        Box(modifier = Modifier.size(TILE_ICON_SIZE)) {
            Box(
                modifier = Modifier.matchParentSize().then(background).then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                DashboardIcon(name = icon, tint = color, modifier = Modifier.size(HASize.X2L))
            }
            tile.badge?.let {
                TileIconBadge(it, Modifier.align(Alignment.TopEnd).offset(x = BADGE_OUTSET, y = -BADGE_OUTSET))
            }
        }
    }
}

/**
 * Port of `ha-tile-badge`: a 16px rounded square in the badge's colour (the primary colour without one) with its
 * icon in white, sticking out 3px past the icon's top end corner as the frontend lays it out.
 *
 * Upstream: `ha-tile-badge` (src/components/tile/ha-tile-badge.ts; src/panels/lovelace/cards/hui-tile-card.ts)
 */
@Composable
private fun TileIconBadge(badge: TileBadge, modifier: Modifier) {
    val background = badge.color?.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
    Box(
        modifier = modifier.size(BADGE_SIZE).clip(RoundedCornerShape(Css.Radius.Md)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(name = badge.icon, tint = Color.White, modifier = Modifier.size(BADGE_ICON_SIZE))
    }
}

/**
 * The tile's name, with its state under it.
 *
 * Upstream: `ha-tile-info` (src/components/tile/ha-tile-info.ts)
 */
@Composable
private fun TileText(tile: TileModel, textAlign: TextAlign, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = tile.name,
            style = TILE_PRIMARY_STYLE.copy(textAlign = textAlign),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.colorTextPrimary,
        )
        tile.state?.let { state ->
            Text(
                text = state,
                style = TILE_SECONDARY_STYLE.copy(textAlign = textAlign),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.colorTextPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun TileCardContentPreview() {
    HAThemeForPreview(modifier = Modifier.padding(Css.Space.S4)) {
        TileCardContent(
            tile = TileModel(
                entityId = "light.kitchen",
                name = "Kitchen lights",
                state = "71%",
                icon = "mdi:lightbulb-group",
                active = true,
                available = true,
                iconInteractive = true,
            ),
        )
    }
}

@Preview
@Composable
private fun VerticalTileCardContentPreview() {
    HAThemeForPreview(modifier = Modifier.padding(Css.Space.S4)) {
        TileCardContent(
            tile = TileModel(
                entityId = "zone.home",
                name = "Home",
                state = "2 people",
                icon = "mdi:home",
                active = true,
                available = true,
                vertical = true,
            ),
        )
    }
}

internal val VERTICAL_TILE_MIN_HEIGHT = 112.dp

/** `ha-tile-container`'s `.content`: 10px padding and gap. */
internal val TILE_PADDING = 10.dp

/** `ha-tile-container`'s `.content` minimum height, `--row-height`. */
internal val TILE_ROW_HEIGHT = 56.dp

/** `--tile-icon-size`. */
internal val TILE_ICON_SIZE = 36.cssPx

/** `ha-tile-badge`'s size, its icon's (`--mdc-icon-size`), and how far it sticks out of the icon. */
private val BADGE_SIZE = 16.cssPx
private val BADGE_ICON_SIZE = 12.cssPx
private val BADGE_OUTSET = 3.cssPx

/** `--tile-icon-opacity`. */
private const val ICON_BACKGROUND_ALPHA = 0.2f

/** `ha-tile-info`'s `.primary`: `--ha-font-size-m`, medium, `--ha-line-height-normal`, 0.1px apart. */
internal val TILE_PRIMARY_STYLE = TextStyle(
    fontSize = Css.FontSize.M,
    fontWeight = Css.FontWeight.Medium,
    lineHeight = Css.LineHeight.Normal,
    letterSpacing = 0.1.cssFontPx,
)

/** `ha-tile-info`'s `.secondary`: `--ha-font-size-s`, normal, `--ha-line-height-condensed`, 0.4px apart. */
internal val TILE_SECONDARY_STYLE = TextStyle(
    fontSize = Css.FontSize.S,
    fontWeight = Css.FontWeight.Normal,
    lineHeight = Css.LineHeight.Condensed,
    letterSpacing = 0.4.cssFontPx,
)
