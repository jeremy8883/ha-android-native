package io.homeassistant.companion.android.dashboard.ui.cards

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

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
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                ) {
                    val color = tile.color.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
                    tile.features.forEach { feature -> TileFeatureControl(feature, tile.available, color, onAction) }
                }
            }
        }
    }
}

@Composable
private fun TileInfo(tile: TileModel, iconModifier: Modifier) {
    if (tile.vertical) {
        VerticalTileInfo(tile, iconModifier)
    } else {
        HorizontalTileInfo(tile, iconModifier)
    }
}

@Composable
private fun HorizontalTileInfo(tile: TileModel, iconModifier: Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(tile, iconModifier)
        TileText(tile, TextAlign.Start, Modifier.weight(1f))
    }
}

@Composable
private fun VerticalTileInfo(tile: TileModel, iconModifier: Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = VERTICAL_TILE_MIN_HEIGHT).padding(vertical = HADimens.SPACE3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterVertically),
    ) {
        TileIcon(tile, iconModifier)
        TileText(tile, TextAlign.Center, Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE3))
    }
}

/** Port of `ha-tile-icon`: the icon in the tile's colour on a circle of it at a fifth of its strength. */
@Composable
private fun TileIcon(tile: TileModel, modifier: Modifier) {
    val color = tile.color.toColor() ?: LocalHAColorScheme.current.colorTextSecondary
    tile.icon?.let { icon ->
        Box(
            modifier = Modifier.size(
                HASize.X5L,
            ).clip(CircleShape).background(color.copy(alpha = ICON_BACKGROUND_ALPHA)).then(modifier),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(name = icon, tint = color, modifier = Modifier.size(HASize.X2L))
        }
    }
}

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
            style = HATextStyle.Body.copy(textAlign = textAlign),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (tile.available) colors.colorTextPrimary else colors.colorTextDisabled,
        )
        tile.state?.let { state ->
            Text(
                text = state,
                style = HATextStyle.BodyMedium.copy(textAlign = textAlign),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (tile.available) colors.colorTextSecondary else colors.colorTextDisabled,
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

private val VERTICAL_TILE_MIN_HEIGHT = 112.dp

/** `--tile-icon-opacity`. */
private const val ICON_BACKGROUND_ALPHA = 0.2f
