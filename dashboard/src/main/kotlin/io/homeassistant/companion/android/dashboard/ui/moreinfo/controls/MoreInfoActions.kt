package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.moreinfo.AutomationMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoAction
import io.homeassistant.companion.android.dashboard.moreinfo.TimerButton
import io.homeassistant.companion.android.dashboard.moreinfo.TimerDuration
import io.homeassistant.companion.android.dashboard.moreinfo.TimerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.timerStartCall
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * Port of the dialogs' `.actions`: plain text buttons, centred and wrapping.
 *
 * Upstream: the `.actions` of `more-info-counter`, `more-info-timer`, `more-info-automation`
 * (src/dialogs/more-info/controls/more-info-counter.ts; src/dialogs/more-info/controls/more-info-timer.ts;
 * src/dialogs/more-info/controls/more-info-automation.ts)
 */
@Composable
internal fun MoreInfoActionRow(actions: List<MoreInfoAction>, onAction: (CardAction) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = Css.Space.S2),
        horizontalArrangement = Arrangement.Center,
    ) {
        actions.forEach { HAPlainButton(it.label, { onAction(it.action) }, enabled = it.enabled) }
    }
}

/**
 * Port of `more-info-automation`: a divider, when it last ran (label and time at either end of the line), and the
 * button running its actions.
 *
 * Upstream: `more-info-automation` (src/dialogs/more-info/controls/more-info-automation.ts)
 */
@Composable
internal fun MoreInfoAutomation(info: AutomationMoreInfo, onAction: (CardAction) -> Unit) {
    val color = LocalHAColorScheme.current.colorTextPrimary
    Column {
        HAHorizontalDivider(Modifier.padding(vertical = Css.Space.S4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(info.lastTriggeredLabel, style = Css.Text.Body, color = color)
            Text(info.lastTriggered, style = Css.Text.Body, color = color)
        }
        MoreInfoActionRow(listOf(info.run), onAction)
    }
}

/**
 * Port of `more-info-timer`: the duration (hours, minutes and seconds, seeded once from the configured one) and
 * the buttons for the timer's state, start and set using the entered duration.
 *
 * Upstream: `more-info-timer` (src/dialogs/more-info/controls/more-info-timer.ts)
 */
@Composable
internal fun MoreInfoTimer(info: TimerMoreInfo, state: EntityState, onAction: (CardAction) -> Unit) {
    var duration by remember(state.entityId) { mutableStateOf(info.duration) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
    ) {
        DurationInput(duration ?: TimerDuration(0, 0, 0, 0)) { duration = it }
        MoreInfoActionRow(
            info.buttons.map { button ->
                val action = when (button) {
                    is TimerButton.Start -> timerStartCall(state, duration)
                    is TimerButton.Call -> button.action
                }
                MoreInfoAction(button.label, enabled = true, action = action)
            },
            onAction,
        )
    }
}

/**
 * Port of `ha-duration-input`: hours, minutes and seconds fields.
 *
 * Upstream: `ha-duration-input` (src/components/ha-duration-input.ts)
 */
@Composable
private fun DurationInput(duration: TimerDuration, onChange: (TimerDuration) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Css.Space.S2), verticalAlignment = Alignment.CenterVertically) {
        DurationField(duration.hours) { onChange(duration.copy(hours = it)) }
        Text(":", style = Css.Text.Body)
        DurationField(duration.minutes, padded = true) { onChange(duration.copy(minutes = it)) }
        Text(":", style = Css.Text.Body)
        DurationField(duration.seconds, padded = true) { onChange(duration.copy(seconds = it)) }
    }
}

/**
 * One number of the duration, zero-padded to two digits when [padded].
 *
 * Upstream: part of `ha-duration-input` (src/components/ha-duration-input.ts)
 */
@Composable
private fun DurationField(value: Int, padded: Boolean = false, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (padded) value.toString().padStart(2, '0') else value.toString()) }
    HATextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(MAX_DIGITS)
            onChange(text.toIntOrNull() ?: 0)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.width(FIELD_WIDTH),
    )
}

private const val MAX_DIGITS = 3
private val FIELD_WIDTH = 72.dp
