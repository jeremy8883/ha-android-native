package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.LawnMowerMoreInfo
import io.homeassistant.companion.android.dashboard.ui.statecontrol.MowerStatus
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The controls of a lawn mower's details, port of `more-info-lawn_mower` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-lawn_mower.ts): the mower drawn as it is, and the start/pause and dock
 * buttons. The battery shows beside the header's time.
 *
 * Upstream: `more-info-lawn_mower` (src/dialogs/more-info/controls/more-info-lawn_mower.ts)
 */
@Composable
internal fun MoreInfoLawnMower(info: LawnMowerMoreInfo, onAction: (CardAction) -> Unit) {
    MoreInfoControlsLayout {
        MowerStatus(info.visual, info.color?.toColor() ?: LocalHAColorScheme.current.colorFillDisabledLoudResting)
        CommandButtonRow(info.buttons, onAction)
    }
}
