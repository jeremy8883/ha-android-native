package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

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
        shape = RoundedCornerShape(HARadius.XL),
    ) {
        Row(content = content)
    }
}
