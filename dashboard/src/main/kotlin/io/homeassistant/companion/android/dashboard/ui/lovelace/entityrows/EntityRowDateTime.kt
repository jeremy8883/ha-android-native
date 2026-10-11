package io.homeassistant.companion.android.dashboard.ui.lovelace.entityrows

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
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
import java.time.ZoneOffset

/**
 * A date and/or time row's fields: the date shows its value and opens the platform's calendar (`ha-date-input`), the
 * time is typed as hours and minutes with AM/PM on a 12-hour clock (`ha-time-input`), setting the entity when done.
 *
 * Upstream: `hui-date-entity-row`, `hui-time-entity-row`, `hui-datetime-entity-row`, `hui-input-datetime-entity-row`
 * (src/panels/lovelace/entity-rows/hui-date-entity-row.ts; src/panels/lovelace/entity-rows/hui-time-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-datetime-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-input-datetime-entity-row.ts)
 */
@Composable
internal fun RowDateTimeControl(control: RowDateTime, onAction: (CardAction) -> Unit) {
    PickerRow {
        if (control.hasDate) DateButton(control, onAction)
        if (control.hasTime) TimeFields(control, onAction)
    }
}

/**
 * The date as a button, opening Android's date picker; the chosen day sets the entity.
 *
 * Upstream: `ha-date-input` (src/components/ha-date-input.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// UPSTREAM-DIFF: Android's own picker; upstream opens its own calendar (the user chose native pickers for fields)
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
