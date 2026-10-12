package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * A card's rounded surface, tinted when [active].
 *
 * Upstream: `ha-card` (src/components/ha-card.ts)
 */
@Composable
internal fun DashboardCardSurface(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.colorFillPrimaryQuietResting else colors.colorSurfaceLow,
        ),
        shape = RoundedCornerShape(Css.Radius.Xl),
    ) {
        Row(content = content)
    }
}
