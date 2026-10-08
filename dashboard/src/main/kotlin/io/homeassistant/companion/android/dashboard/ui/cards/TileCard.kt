package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

@Composable
internal fun TileCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val tile by remember(card) { derivedStateOf { tileModel(card, hass.value?.states.orEmpty()) } }
    val model = tile
    if (model == null) {
        UnsupportedCard(stringResource(R.string.native_dashboard_entity_not_found, card.entity.orEmpty()), modifier)
    } else {
        TileCardContent(model, modifier)
    }
}

@Composable
internal fun TileCardContent(tile: TileModel, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier, active = tile.active) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tile.icon != null) {
                Box(modifier = Modifier.size(HASize.X5L), contentAlignment = Alignment.Center) {
                    DashboardIcon(
                        name = tile.icon,
                        tint = when {
                            !tile.available -> colors.colorTextDisabled
                            tile.active -> colors.colorOnPrimaryQuiet
                            else -> colors.colorTextSecondary
                        },
                        modifier = Modifier.size(HASize.X2L),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tile.name,
                    style = HATextStyle.Body,
                    color = if (tile.available) colors.colorTextPrimary else colors.colorTextDisabled,
                )
                tile.stateText?.let { state ->
                    Text(
                        text = state,
                        style = HATextStyle.BodyMedium,
                        color = if (tile.available) colors.colorTextSecondary else colors.colorTextDisabled,
                    )
                }
            }
        }
    }
}

private val TileModel.stateText: String?
    get() = listOfNotNull(state, unit).takeIf(List<String>::isNotEmpty)?.joinToString(" ")

@Preview
@Composable
private fun TileCardContentPreview() {
    HAThemeForPreview(modifier = Modifier.padding(HADimens.SPACE4)) {
        TileCardContent(
            tile = TileModel(
                entityId = "light.kitchen",
                name = "Kitchen lights",
                state = "On",
                unit = null,
                icon = "mdi:lightbulb-group",
                active = true,
                available = true,
            ),
        )
    }
}
