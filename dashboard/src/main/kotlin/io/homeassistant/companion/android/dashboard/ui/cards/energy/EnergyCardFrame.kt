package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.DEFAULT_ENERGY_COLLECTION_KEY
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCardSurface

/**
 * An energy card: its title (`ha-card`'s header), then [content] with its collection's data; "Loading…" until there
 * is data, as the frontend's cards show, and an error when it couldn't be loaded rather than nothing.
 */
@Composable
internal fun EnergyCardFrame(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(EnergyData) -> Unit,
) {
    val key = card.json.string("collection_key") ?: DEFAULT_ENERGY_COLLECTION_KEY
    val collection by remember(key) { derivedStateOf { hass.value?.energy?.get(key) } }
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier) {
        Column(Modifier.fillMaxWidth()) {
            card.json.string("title")?.let { title ->
                Text(
                    title,
                    style = HATextStyle.HeadlineMedium,
                    color = colors.colorTextPrimary,
                    modifier = Modifier.padding(start = HADimens.SPACE4, end = HADimens.SPACE4, top = HADimens.SPACE4),
                )
            }
            val data = collection?.data
            when {
                data != null -> content(data)
                collection?.failed == true -> Message(stringResource(R.string.native_dashboard_energy_failed))
                else -> Message(hass.value?.localize?.invoke(LOADING).orEmpty())
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = HATextStyle.Body,
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.padding(HADimens.SPACE4),
    )
}

private const val LOADING = "ui.panel.lovelace.cards.energy.loading"
