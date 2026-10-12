package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * A placeholder saying that [label] can't be shown.
 *
 * Upstream: none (native: a placeholder where the native dashboard can't draw a card)
 */
@Composable
internal fun UnsupportedCard(label: String, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.native_dashboard_unsupported_card, label),
            style = Css.Text.Body.copy(textAlign = TextAlign.Center),
            color = colors.colorTextSecondary,
            modifier = Modifier.padding(Css.Space.S3),
        )
    }
}
