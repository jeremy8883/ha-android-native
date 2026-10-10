package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.LawnMowerMoreInfo
import io.homeassistant.companion.android.dashboard.ui.controls.PulsingStatusIcon

/**
 * The controls of a lawn mower's details, port of `more-info-lawn_mower` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-lawn_mower.ts): its icon (pulsing while it mows or heads home, in place
 * of upstream's drawing) and the start/pause and dock buttons. The battery shows beside the header's time.
 */
@Composable
internal fun MoreInfoLawnMower(info: LawnMowerMoreInfo, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        PulsingStatusIcon(info.status, pulsing = info.busy)
        CommandButtonRow(info.buttons, onAction)
    }
}
