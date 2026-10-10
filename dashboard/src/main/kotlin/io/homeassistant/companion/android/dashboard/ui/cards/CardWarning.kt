package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Port of `hui-warning`: why a card can't show, in its place. */
@Composable
internal fun CardWarning(text: String, modifier: Modifier = Modifier) {
    DashboardCardSurface(modifier = modifier) {
        Text(
            text,
            style = HATextStyle.BodyMedium,
            color = LocalHAColorScheme.current.colorOnWarningNormal,
            modifier = Modifier.padding(HADimens.SPACE3),
        )
    }
}
