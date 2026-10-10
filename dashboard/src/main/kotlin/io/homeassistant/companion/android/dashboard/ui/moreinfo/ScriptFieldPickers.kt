package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// The date and time editors of a script's fields, through the platform's pickers.

/** `ha-selector-datetime`: the date and the time, "yyyy-MM-dd HH:mm:ss" (a time picked first is on [today]). */
@Composable
internal fun DateTimeEditor(text: String?, enabled: Boolean, today: LocalDate, onChange: (JsonElement?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        val (date, time) = text.orEmpty().split(' ').let { it.getOrNull(0)?.ifEmpty { null } to it.getOrNull(1) }
        DateEditor(date, enabled) { onChange(JsonPrimitive("$it ${time ?: MIDNIGHT}")) }
        TimeEditor(time, enabled) { onChange(JsonPrimitive("${date ?: today} $it")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateEditor(date: String?, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    HAPlainButton(date ?: "—", { open = true }, enabled = enabled)
    if (!open) return
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), {
                open = false
                picker.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
            })
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), { open = false }) },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeEditor(time: String?, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    HAPlainButton(time ?: "—", { open = true }, enabled = enabled)
    if (!open) return
    val current = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val picker = rememberTimePickerState(initialHour = current?.hour ?: 0, initialMinute = current?.minute ?: 0)
    AlertDialog(
        onDismissRequest = { open = false },
        text = { TimePicker(state = picker) },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), {
                open = false
                onPick(LocalTime.of(picker.hour, picker.minute).format(SECONDS_TIME))
            })
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), { open = false }) },
    )
}

private const val MIDNIGHT = "00:00:00"
private val SECONDS_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
