package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.areaCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

@Composable
internal fun AreaCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val area by remember(card) { derivedStateOf { hass.value?.areaCardModel(card) } }
    val model = area ?: return UnsupportedCard(card.type.orEmpty(), modifier)
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            DashboardIcon(
                name = model.icon,
                tint = colors.colorOnPrimaryNormal,
                modifier = Modifier.size(HASize.X4L),
            )
            Text(text = model.name, style = HATextStyle.Body, color = colors.colorTextPrimary)
        }
    }
}
