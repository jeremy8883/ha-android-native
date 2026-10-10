package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus

/** The smaller domains' controls: a siren's, a counter's, an automation's, a timer's, a remote's and the inputs. */
@Composable
internal fun SimpleDomainControls(model: MoreInfoModel, state: EntityState, onAction: (CardAction) -> Unit) {
    model.siren?.let { MoreInfoSiren(it, state, onAction) }
    model.counter?.let { MoreInfoActionRow(it, onAction) }
    model.automation?.let { MoreInfoAutomation(it, onAction) }
    model.timer?.let { MoreInfoTimer(it, state, onAction) }
    model.remote?.let { ControlSelectMenus(listOf(it), onAction) }
    model.input?.let { MoreInfoInput(it, onAction) }
}
