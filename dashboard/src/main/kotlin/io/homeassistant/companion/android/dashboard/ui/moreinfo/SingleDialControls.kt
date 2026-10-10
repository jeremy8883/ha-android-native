package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.CircularControl
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.ui.controls.CircularStateControl
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus

/**
 * The controls of a single-target dial domain (`more-info-water_heater`, `more-info-humidifier`): the current
 * readings, the dial set to [target] (each new target sent through [onSet]), and the menus.
 */
@Composable
internal fun SingleDialControls(
    current: List<Pair<String, String>>,
    control: CircularControl,
    target: Double?,
    menus: List<SelectMenu>,
    onSet: (Double) -> Unit,
    onAction: (CardAction) -> Unit,
    bottomUnit: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        CurrentReadings(current)
        CircularStateControl(
            control = control,
            targets = CircularTargets(target, null, null),
            onSet = { targets, _ -> targets.value?.let(onSet) },
            bottomUnit = bottomUnit,
        )
        ControlSelectMenus(menus, onAction)
    }
}
