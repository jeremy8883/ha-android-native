package io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import io.homeassistant.companion.android.dashboard.ui.theme.FrontendTextStyle
import java.time.LocalTime

/**
 * Port of `ha-time-input`: an hours box ("hh"), a minutes box ("mm") and, on a 12-hour clock, AM or PM. The time is
 * set when a box is left or done, or AM/PM changes, keeping the seconds.
 *
 * Upstream: `ha-time-input`, `ha-base-time-input` (src/components/ha-time-input.ts;
 * src/components/ha-base-time-input.ts)
 */
@Composable
internal fun TimeFields(control: RowDateTime, onAction: (CardAction) -> Unit) {
    val current = control.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    var hours by remember(control.time) {
        mutableStateOf(current?.let { shownHour(it.hour, control.amPm).toString() }.orEmpty())
    }
    var minutes by remember(control.time) {
        mutableStateOf(current?.minute?.let { "%02d".format(java.util.Locale.ROOT, it) }.orEmpty())
    }
    var pm by remember(control.time) { mutableStateOf((current?.hour ?: 0) >= NOON) }
    val commit: () -> Unit = {
        typedTime(
            hours,
            minutes,
            pm.takeIf {
                control.amPm
            },
            current?.second ?: 0,
        )?.takeIf { it != current }?.let { time ->
            control.setTime(time.toString().let { if (it.length == SHORT_TIME) "$it:00" else it })?.let(onAction)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
        TimeBox(hours, "hh", control.enabled, { hours = it }, commit)
        Text(":", style = FrontendTextStyle.Body)
        TimeBox(minutes, "mm", control.enabled, { minutes = it }, commit)
        if (control.amPm) {
            HADropdownMenu(
                items = listOf(HADropdownItem(false, "AM"), HADropdownItem(true, "PM")),
                selectedKey = pm,
                onItemSelected = {
                    pm = it
                    commit()
                },
                enabled = control.enabled,
                modifier = Modifier.width(AM_PM_WIDTH),
            )
        }
    }
}

/**
 * One two-digit box of the time (hours or minutes), handing it over when done or left.
 *
 * Upstream: part of `ha-base-time-input` (src/components/ha-base-time-input.ts)
 */
@Composable
private fun TimeBox(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    HATextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.width(BOX_WIDTH).onFocusChanged { state ->
            if (focused && !state.isFocused) onDone()
            focused = state.isFocused
        },
    )
}

/** The time typed as [hours] and [minutes] (on a 12-hour clock with [pm] when given); `null` when not a time. */
private fun typedTime(hours: String, minutes: String, pm: Boolean?, seconds: Int): LocalTime? {
    val hour = hours.toIntOrNull()
    val minute = minutes.toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
    val hour24 = when {
        hour == null -> null
        pm == null -> hour.takeIf { it in 0..MAX_HOUR }
        else -> hour.takeIf { it in 1..NOON }?.let { it % NOON + if (pm) NOON else 0 }
    }
    return if (hour24 != null && minute != null) LocalTime.of(hour24, minute, seconds) else null
}

/** The hour as the clock shows it: 12 for midnight and noon, 1 to 11 otherwise on a 12-hour clock. */
private fun shownHour(hour: Int, amPm: Boolean): Int = if (amPm) (hour % NOON).takeIf { it != 0 } ?: NOON else hour

private const val NOON = 12
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val SHORT_TIME = 5
private val BOX_WIDTH = 64.dp
private val AM_PM_WIDTH = 96.dp
