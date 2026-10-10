package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.LawnMowerMoreInfo
import io.homeassistant.companion.android.dashboard.ui.controls.MowerStatus
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The controls of a lawn mower's details, port of `more-info-lawn_mower` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-lawn_mower.ts): the mower drawn as it is, and the start/pause and dock
 * buttons. The battery shows beside the header's time.
 */
@Composable
internal fun MoreInfoLawnMower(info: LawnMowerMoreInfo, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        MowerStatus(info.visual, info.color?.toColor() ?: LocalHAColorScheme.current.colorFillDisabledLoudResting)
        CommandButtonRow(info.buttons, onAction)
    }
}
