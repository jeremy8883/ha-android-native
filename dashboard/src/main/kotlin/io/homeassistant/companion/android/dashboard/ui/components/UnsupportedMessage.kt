package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R

/**
 * What can't be shown natively, with a button opening it in the companion app when other pages can be opened.
 *
 * Upstream: none (native: what the native dashboards can't show)
 */
@Composable
internal fun UnsupportedMessage(text: String, onOpenElsewhere: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HADimens.SPACE6),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = HATextStyle.Body, color = LocalHAColorScheme.current.colorTextSecondary)
        onOpenElsewhere?.let { HAAccentButton(stringResource(R.string.native_dashboard_open_elsewhere), it) }
    }
}
