package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures.TileFeatureControl
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
        Column(modifier = Modifier.fillMaxWidth()) {
            TileInfo(tile, iconModifier)
            if (tile.features.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(
                        start = HADimens.SPACE3,
                        end = HADimens.SPACE3,
                        bottom = HADimens.SPACE3,
                    ),
                    // `hui-card-features`' gap
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
                ) {
                    val color = tile.color.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
                    tile.features.forEach { feature -> TileFeatureControl(feature, tile.available, color, onAction) }
                }
            }
        }
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
        TileText(tile, TextAlign.Center, Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE3))
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
        Box(
            modifier = Modifier.size(TILE_ICON_SIZE).then(background).then(modifier),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(name = icon, tint = color, modifier = Modifier.size(HASize.X2L))
        }
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
    HAThemeForPreview(modifier = Modifier.padding(HADimens.SPACE4)) {
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
    HAThemeForPreview(modifier = Modifier.padding(HADimens.SPACE4)) {
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
internal val TILE_ICON_SIZE = 36.dp

/** `--tile-icon-opacity`. */
private const val ICON_BACKGROUND_ALPHA = 0.2f

/** `ha-tile-info`'s `.primary`: `--ha-font-size-m`, medium, `--ha-line-height-normal`, 0.1px apart. */
internal val TILE_PRIMARY_STYLE = TextStyle(
    fontSize = HAFontSize.M,
    fontWeight = FontWeight.Medium,
    lineHeight = 1.6.em,
    letterSpacing = 0.1.sp,
)

/** `ha-tile-info`'s `.secondary`: `--ha-font-size-s`, normal, `--ha-line-height-condensed`, 0.4px apart. */
internal val TILE_SECONDARY_STYLE = TextStyle(
    fontSize = HAFontSize.S,
    fontWeight = FontWeight.Normal,
    lineHeight = 1.2.em,
    letterSpacing = 0.4.sp,
)
