package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.dashboard.R

/** What can't be shown natively, with a button opening it in the companion app when other pages can be opened. */
@Composable
internal fun UnsupportedMessage(text: String, onOpenElsewhere: (() -> Unit)?) {
    Column(Modifier.padding(HADimens.SPACE4), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        Text(text, style = HATextStyle.Body)
        onOpenElsewhere?.let { HAAccentButton(stringResource(R.string.native_dashboard_open_elsewhere), it) }
    }
}
