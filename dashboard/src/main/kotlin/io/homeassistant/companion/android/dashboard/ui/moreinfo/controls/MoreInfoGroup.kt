package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
import io.homeassistant.companion.android.dashboard.ui.moreinfo.DomainControls
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoStateText
import io.homeassistant.companion.android.dashboard.ui.statesummary.StateCardRow
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.Instant

/**
 * Port of `more-info-group`: the members' shared domain's details for the group (its header and controls, whose
 * calls go to the group), then each member's state card.
 *
 * Upstream: `more-info-group` (src/dialogs/more-info/controls/more-info-group.ts)
 */
@Composable
internal fun MoreInfoGroup(
    group: MoreInfoExtra.Group,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        val inner = group.inner
        val innerState = group.innerState
        if (inner != null && innerState != null) {
            val innerHass = remember(hass, innerState) {
                hass.copy(
                    states =
                    hass.states + (innerState.entityId to innerState),
                )
            }
            val toGroup = { action: CardAction -> onAction(group.toGroup(action)) }
            // The state header keeps its own 20px under it
            if (inner.stateHeader) MoreInfoStateText(inner)
            Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S4)) {
                DomainControls(inner, innerState, innerHass, now, toGroup)
            }
        }
        group.members.forEach { member -> StateCardRow(member, now, onAction) }
    }
}
