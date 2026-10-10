package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowControl
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import io.homeassistant.companion.android.dashboard.derive.RowNumberBox
import io.homeassistant.companion.android.dashboard.derive.RowSelect
import io.homeassistant.companion.android.dashboard.derive.RowSlider
import io.homeassistant.companion.android.dashboard.derive.RowTextInput
import io.homeassistant.companion.android.dashboard.ui.cards.RowDateTimeControl
import io.homeassistant.companion.android.dashboard.ui.cards.RowNumberBoxControl
import io.homeassistant.companion.android.dashboard.ui.cards.RowSelectControl
import io.homeassistant.companion.android.dashboard.ui.cards.RowSliderControl
import io.homeassistant.companion.android.dashboard.ui.cards.RowTextControl

/** The input upstream's details lead with for numbers, selects, texts, dates and times: the row's control. */
@Composable
internal fun MoreInfoInput(control: RowControl, onAction: (CardAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (control) {
            is RowSlider -> RowSliderControl(control, onAction)
            is RowNumberBox -> RowNumberBoxControl(control, onAction)
            is RowSelect -> RowSelectControl(control, onAction)
            is RowTextInput -> RowTextControl(control, onAction)
            is RowDateTime -> RowDateTimeControl(control, onAction)
            else -> Unit
        }
    }
}
