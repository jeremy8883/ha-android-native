package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.headingModel
import io.homeassistant.companion.android.dashboard.model.CardConfig

@Composable
internal fun HeadingCard(card: CardConfig, modifier: Modifier = Modifier) {
    val heading = remember(card) { headingModel(card) }
    if (heading.text.isEmpty()) return
    val colors = LocalHAColorScheme.current
    Row(
        modifier = modifier.padding(
            start = HADimens.SPACE1,
            top = HADimens.SPACE4,
            end = HADimens.SPACE1,
            bottom = HADimens.SPACE1,
        ),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(
            name = heading.icon,
            tint = colors.colorTextSecondary,
            modifier = Modifier.size(if (heading.isSubtitle) HASize.L else HASize.XL),
        )
        Text(
            text = heading.text,
            style = if (heading.isSubtitle) HATextStyle.Body else HATextStyle.HeadlineMedium,
            color = colors.colorTextPrimary,
        )
    }
}
