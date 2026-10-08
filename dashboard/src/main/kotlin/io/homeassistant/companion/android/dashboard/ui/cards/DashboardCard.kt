package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.areaCardModel
import io.homeassistant.companion.android.dashboard.derive.headingModel
import io.homeassistant.companion.android.dashboard.derive.tapNavigationPath
import io.homeassistant.companion.android.dashboard.derive.tileModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/** Renders [card] with its native renderer, or a placeholder when the type is not supported yet. */
@Composable
internal fun DashboardCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A navigate tap action works on any card, so subviews are reachable before every card type is native
    val clickModifier = card.tapNavigationPath
        ?.let { path -> modifier.clickable(role = Role.Button) { onNavigate(path) } }
        ?: modifier
    when (card.type) {
        CARD_TILE -> TileCard(card, hass, clickModifier)
        CARD_HEADING -> HeadingCard(card, clickModifier)
        CARD_AREA -> AreaCard(card, hass, clickModifier)
        else -> UnsupportedCard(card.type.orEmpty(), clickModifier)
    }
}

@Composable
private fun TileCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier) {
    // Recomposes only when this tile's derived content changes, not on every entity update
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (tile.active) colors.colorFillPrimaryQuietResting else colors.colorSurfaceLow,
        ),
    ) {
        Column(Modifier.padding(HADimens.SPACE3)) {
            Text(tile.name, style = HATextStyle.Body, color = colors.colorTextPrimary)
            if (tile.state != null) {
                Text(
                    listOfNotNull(tile.state, tile.unit).joinToString(" "),
                    style = HATextStyle.BodyMedium,
                    color = if (tile.available) colors.colorTextSecondary else colors.colorTextDisabled,
                )
            }
        }
    }
}

@Composable
private fun HeadingCard(card: CardConfig, modifier: Modifier) {
    val heading = remember(card) { headingModel(card) }
    if (heading.text.isEmpty()) return
    Text(
        heading.text,
        style = if (heading.isSubtitle) HATextStyle.Body else HATextStyle.HeadlineMedium,
        color = LocalHAColorScheme.current.colorTextPrimary,
        modifier = modifier.padding(top = HADimens.SPACE2),
    )
}

@Composable
private fun AreaCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier) {
    val area by remember(card) { derivedStateOf { hass.value?.areaCardModel(card) } }
    val model = area ?: return UnsupportedCard(card.type.orEmpty(), modifier)
    val colors = LocalHAColorScheme.current
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = colors.colorSurfaceLow)) {
        Text(
            model.name,
            style = HATextStyle.Body,
            color = colors.colorTextPrimary,
            modifier = Modifier.padding(HADimens.SPACE4),
        )
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
private const val CARD_HEADING = "heading"
private const val CARD_AREA = "area"
