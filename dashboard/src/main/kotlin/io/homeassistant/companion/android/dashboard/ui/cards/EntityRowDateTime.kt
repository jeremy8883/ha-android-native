package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * A date and/or time row's pickers: each shows its value and opens the platform's picker (upstream's
 * `ha-date-input` and `ha-time-input` are the browser's own), setting the entity when confirmed.
 */
@Composable
internal fun RowDateTimeControl(control: RowDateTime, onAction: (CardAction) -> Unit) {
    PickerRow {
        if (control.hasDate) DateButton(control, onAction)
        if (control.hasTime) TimeButton(control, onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(control: RowDateTime, onAction: (CardAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    HAPlainButton(control.dateText ?: control.label ?: "—", { open = true }, enabled = control.enabled)
    if (!open) return
    // The picker works in UTC days
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = control.date?.let {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), {
                open = false
                picker.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    control.setDate(day)?.let(onAction)
                }
            })
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), { open = false }) },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(control: RowDateTime, onAction: (CardAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    HAPlainButton(control.timeText ?: "—", { open = true }, enabled = control.enabled)
    if (!open) return
    val current = control.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val picker = rememberTimePickerState(initialHour = current?.hour ?: 0, initialMinute = current?.minute ?: 0)
    AlertDialog(
        onDismissRequest = { open = false },
        text = { TimePicker(state = picker) },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), {
                open = false
                // Upstream's time input keeps the seconds at zero when only hours and minutes show
                val time = LocalTime.of(picker.hour, picker.minute).toString().let {
                    if (it.length ==
                        SHORT_TIME
                    ) {
                        "$it:00"
                    } else {
                        it
                    }
                }
                control.setTime(time)?.let(onAction)
            })
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), { open = false }) },
    )
}

private const val SHORT_TIME = 5
