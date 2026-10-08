package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R

@Composable
internal fun UnsupportedCard(label: String, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.native_dashboard_unsupported_card, label),
            style = HATextStyle.BodyMedium,
            color = colors.colorTextSecondary,
            modifier = Modifier.padding(HADimens.SPACE3),
        )
    }
}
