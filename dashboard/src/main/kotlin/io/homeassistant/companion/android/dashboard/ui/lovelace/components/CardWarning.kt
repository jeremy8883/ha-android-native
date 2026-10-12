package io.homeassistant.companion.android.dashboard.ui.lovelace.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * Port of `hui-warning`: why a card can't show, in its place.
 *
 * Upstream: `hui-warning` (src/panels/lovelace/components/hui-warning.ts)
 */
@Composable
internal fun CardWarning(text: String, modifier: Modifier = Modifier) {
    DashboardCardSurface(modifier = modifier) {
        Text(
            text,
            style = Css.Text.Body,
            color = LocalHAColorScheme.current.colorOnWarningNormal,
            modifier = Modifier.padding(Css.Space.S3),
        )
    }
}
