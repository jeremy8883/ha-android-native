package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.ThermostatCardModel
import io.homeassistant.companion.android.dashboard.derive.thermostatCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTarget
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import io.homeassistant.companion.android.dashboard.moreinfo.climateTargets
import io.homeassistant.companion.android.dashboard.moreinfo.climateTemperatureCall
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTemperatureCall
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures.TileFeatureControl
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.statecontrol.CircularStateControl
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * A thermostat card, port of `hui-thermostat-card` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-thermostat-card.ts): the entity's name above the temperature dial of its details,
 * a button opening them, and its features under the dial.
 *
 * Upstream: `hui-thermostat-card` (src/panels/lovelace/cards/hui-thermostat-card.ts)
 */
@Composable
internal fun ThermostatCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val thermostat by remember(card) { derivedStateOf { hass.value?.thermostatCardModel(card) } }
    val model = when (val shown = thermostat) {
        null -> return
        is ThermostatCardModel.Warning -> return CardWarning(shown.text, modifier)
        is ThermostatCardModel.Shown -> shown
    }
    val state = model.state
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    model.name,
                    style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium),
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = HADimens.SPACE12, vertical = HADimens.SPACE3),
                )
                if (model.waterHeater) {
                    CircularStateControl(
                        control = model.control,
                        targets = CircularTargets(waterHeaterTarget(state), null, null),
                        onSet = { targets, _ ->
                            targets.value?.let { interactions.onAction(waterHeaterTemperatureCall(state, it)) }
                        },
                    )
                } else {
                    CircularStateControl(
                        control = model.control,
                        targets = climateTargets(state),
                        onSet = { targets, target ->
                            interactions.onAction(
                                climateTemperatureCall(
                                    state,
                                    targets,
                                    range =
                                    target != CircularTarget.Value,
                                ),
                            )
                        },
                        secondary = model.secondary,
                    )
                }
                ThermostatFeatures(model, interactions)
            }
            IconButton(
                onClick = { interactions.onAction(CardAction.MoreInfo(state.entityId)) },
                modifier = Modifier.align(Alignment.TopEnd).semantics { contentDescription = model.moreInfoLabel },
            ) {
                DashboardIcon("mdi:dots-vertical", colors.colorTextSecondary, Modifier.size(HASize.X2L))
            }
        }
    }
}

/**
 * The card's features under the dial, in the entity's colour (`--feature-color`).
 *
 * Upstream: `hui-card-features` in `hui-thermostat-card` (src/panels/lovelace/card-features/hui-card-features.ts;
 * src/panels/lovelace/cards/hui-thermostat-card.ts)
 */
@Composable
private fun ThermostatFeatures(model: ThermostatCardModel.Shown, interactions: CardInteractions) {
    if (model.features.isEmpty()) return
    val color = model.featureColor?.toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE3),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        model.features.forEach { feature -> TileFeatureControl(feature, model.available, color, interactions.onAction) }
    }
}
