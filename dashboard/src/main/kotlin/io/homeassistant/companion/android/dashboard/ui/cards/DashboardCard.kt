package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.model.CardConfig

/** Renders [card] with its native renderer, or a placeholder when the type is not supported yet. */
@Composable
internal fun DashboardCard(card: CardConfig, entityStates: State<EntityStates?>, modifier: Modifier = Modifier) {
    when (card.type) {
        CARD_TILE -> TileCard(card, entityStates, modifier)
        else -> UnsupportedCard(card.type.orEmpty(), modifier)
    }
}

@Composable
private fun TileCard(card: CardConfig, entityStates: State<EntityStates?>, modifier: Modifier) {
    // Recomposes only when this tile's derived content changes, not on every entity update
    val tile by remember(card) { derivedStateOf { tileModel(card, entityStates.value.orEmpty()) } }
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (tile.active) colors.colorFillPrimaryQuietResting else colors.colorSurfaceLow,
        ),
    ) {
        Column(Modifier.padding(HADimens.SPACE3)) {
            Text(tile.name, style = HATextStyle.Body, color = colors.colorTextPrimary)
            Text(
                listOfNotNull(tile.state, tile.unit).joinToString(" "),
                style = HATextStyle.BodyMedium,
                color = if (tile.available) colors.colorTextSecondary else colors.colorTextDisabled,
            )
        }
    }
}

@Composable
private fun UnsupportedCard(label: String, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = colors.colorSurfaceLow)) {
        Text(
            stringResource(R.string.native_dashboard_unsupported_card, label),
            style = HATextStyle.BodyMedium,
            color = colors.colorTextSecondary,
            modifier = Modifier.padding(HADimens.SPACE3),
        )
    }
}

private const val CARD_TILE = "tile"
