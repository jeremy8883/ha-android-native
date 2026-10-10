package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.ButtonSlot
import io.homeassistant.companion.android.dashboard.moreinfo.PositionButton
import io.homeassistant.companion.android.dashboard.moreinfo.PositionButtons
import io.homeassistant.companion.android.dashboard.moreinfo.PositionMoreInfo
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.IconToggle
import io.homeassistant.companion.android.dashboard.ui.controls.IconToggleGroup
import io.homeassistant.companion.android.dashboard.ui.controls.STATE_CONTROL_HEIGHT
import io.homeassistant.companion.android.dashboard.ui.controls.STATE_CONTROL_RADIUS
import io.homeassistant.companion.android.dashboard.ui.controls.StateControlSlider
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import kotlin.math.roundToInt

/**
 * The controls of a cover's or valve's details, port of `more-info-cover` and `more-info-valve`
 * (frontend@20260624.6 src/dialogs/more-info/controls/): the position (and tilt) sliders, or the buttons (a switch
 * when it only opens and closes), a pair of buttons to switch between them, and the favourite positions.
 */
@Composable
internal fun MoreInfoPosition(
    info: PositionMoreInfo,
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
) {
    var buttons by rememberSaveable(state.entityId) { mutableStateOf(!info.positionFirst) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        if (buttons) {
            info.toggle?.let { StateToggleControl(it, onAction) }
            info.buttons?.let { PositionButtonsView(it, onAction) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
                listOfNotNull(info.position, info.tilt).forEach { slider ->
                    StateControlSlider(slider, valueText = { "${it.roundToInt()}%" }, onAction = onAction)
                }
            }
        }
        info.modeLabels?.let { (position, button) ->
            val enabled = state.state != UNAVAILABLE
            IconToggleGroup(
                toggles = listOf(
                    IconToggle("mdi:menu", position, !buttons, enabled),
                    IconToggle("mdi:swap-vertical", button, buttons, enabled),
                ),
                onSelect = { buttons = it == 1 },
            )
        }
        PositionFavoritesSection(state, hass, onAction)
    }
}

/** Port of the buttons' `ha-control-button-group` (a column) or their cross, around stop. */
@Composable
private fun PositionButtonsView(buttons: PositionButtons, onAction: (CardAction) -> Unit) {
    when (buttons) {
        is PositionButtons.Line -> Column(
            modifier = Modifier.width(LINE_WIDTH).height(STATE_CONTROL_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            buttons.buttons.forEach { TallButton(it, onAction, Modifier.weight(1f).fillMaxWidth()) }
        }
        is PositionButtons.Cross -> Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            CROSS_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                    row.forEach { slot ->
                        val button = buttons.buttons.firstOrNull { it.slot == slot }
                        if (button != null) {
                            TallButton(button, onAction, Modifier.size(CROSS_CELL))
                        } else {
                            Box(Modifier.size(CROSS_CELL))
                        }
                    }
                }
            }
        }
    }
}

/** Port of `ha-control-button`: a rounded button with its icon, greyed while it can't be used. */
@Composable
private fun TallButton(button: PositionButton, onAction: (CardAction) -> Unit, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(STATE_CONTROL_RADIUS))
            .background(
                if (button.enabled) colors.colorFillNeutralNormalResting else colors.colorFillNeutralQuietResting,
            )
            .clickable(enabled = button.enabled, role = Role.Button) { button.actions.forEach(onAction) }
            .semantics { contentDescription = button.label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(
            button.icon,
            if (button.enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet,
            Modifier.size(HASize.X2L),
        )
    }
}

private const val UNAVAILABLE = "unavailable"
private val LINE_WIDTH = 100.dp
private val CROSS_CELL = 100.dp
private val BUTTON_GAP = 10.dp

/** The cross's grid: open above stop, the tilt either side, close below. */
private val CROSS_ROWS: List<List<ButtonSlot?>> = listOf(
    listOf(null, ButtonSlot.Open, null),
    listOf(ButtonSlot.CloseTilt, ButtonSlot.Stop, ButtonSlot.OpenTilt),
    listOf(null, ButtonSlot.Close, null),
)
