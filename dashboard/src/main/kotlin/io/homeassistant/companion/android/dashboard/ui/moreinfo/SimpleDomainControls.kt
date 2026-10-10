package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus

/** The smaller domains' controls: a siren's, a counter's, an automation's, a timer's, a remote's, the inputs and an update's. */
@Composable
internal fun SimpleDomainControls(model: MoreInfoModel, state: EntityState, onAction: (CardAction) -> Unit) {
    model.lawnMower?.let { MoreInfoLawnMower(it, onAction) }
    model.siren?.let { MoreInfoSiren(it, state, onAction) }
    model.counter?.let { MoreInfoActionRow(it, onAction) }
    model.automation?.let { MoreInfoAutomation(it, onAction) }
    model.timer?.let { MoreInfoTimer(it, state, onAction) }
    model.remote?.let { ControlSelectMenus(listOf(it), onAction) }
    // Its footer is pinned under the details
    model.update?.let { MoreInfoUpdate(it, state.entityId) }
}
