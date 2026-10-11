package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.composable.HAInputChip
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.panels.DateRange
import io.homeassistant.companion.android.dashboard.panels.RangePreset
import io.homeassistant.companion.android.dashboard.panels.dateRangeText
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Port of `ha-date-range-picker` with `time-picker`: the period's ends in a field that opens the picker, with arrows
 * to the period before and after, as long as it.
 */
@Composable
internal fun DateRangeField(
    range: DateRange,
    hass: HassSnapshot,
    now: ZonedDateTime,
    presets: List<RangePreset>,
    onChange: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        PresetChips(range, hass, now, presets, onChange)
        RangeRow(range, hass, now, onOpen = { open = true }, onChange)
    }
    if (open) {
        DateRangeDialog(range, hass, onDismiss = { open = false }) {
            open = false
            onChange(it)
        }
    }
}

/**
 * The presets ("Today", "Yesterday", "This week"…) on a row that scrolls, the one the period is as of [now] selected;
 * a tap picks its period.
 */
// UPSTREAM-DIFF: the presets sit on the page above the field, the period's one selected; upstream lists them inside
// its picker (the user asked for them on the page)
@Composable
private fun PresetChips(
    range: DateRange,
    hass: HassSnapshot,
    now: ZonedDateTime,
    presets: List<RangePreset>,
    onChange: (DateRange) -> Unit,
) {
    val firstDayOfWeek = DayOfWeek.of(hass.formats.firstWeekday)
    val selected = RangePreset.matching(range, presets, now, firstDayOfWeek)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        presets.forEach { preset ->
            HAInputChip(
                text = hass.localize("$PICKER.ranges.${preset.key}"),
                onClick = { onChange(preset.range(now, firstDayOfWeek)) },
                selected = preset == selected,
            )
        }
    }
}

/** The period's field, opening the picker, with the arrows to the period before and after. */
@Composable
private fun RangeRow(
    range: DateRange,
    hass: HassSnapshot,
    now: ZonedDateTime,
    onOpen: () -> Unit,
    onChange: (DateRange) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        FilledField(
            label = "${hass.localize("$PICKER.start_date")} - ${hass.localize("$PICKER.end_date")}",
            value = hass.formats.dateRangeText(range, now.toLocalDate()),
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
        )
        ArrowButton("mdi:chevron-left", hass.localize("ui.common.previous")) {
            onChange(range.shift(forward = false, hass.formats.zone))
        }
        ArrowButton("mdi:chevron-right", hass.localize("ui.common.next")) {
            onChange(range.shift(forward = true, hass.formats.zone))
        }
    }
}

/**
 * The read-only field the frontend draws (a filled `ha-textarea`): a small label over the value on the input fill,
 * with a line under it.
 */
@Composable
private fun FilledField(label: String, value: String, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = LocalHAColorScheme.current
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = HADimens.SPACE1, topEnd = HADimens.SPACE1))
            .background(resolveVariable("mdc-text-field-fill-color", dark) ?: colors.colorFillNeutralQuietResting),
    ) {
        Column(
            Modifier.padding(
                start = HADimens.SPACE4,
                end = HADimens.SPACE4,
                top = HADimens.SPACE4,
                bottom = HADimens.SPACE3,
            ),
        ) {
            Text(
                label,
                style = HATextStyle.BodyMedium.copy(fontSize = LABEL_SIZE, textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
            Text(value, style = HATextStyle.Body.copy(textAlign = TextAlign.Start), color = colors.colorTextPrimary)
        }
        HorizontalDivider(
            color =
            resolveVariable("mdc-text-field-idle-line-color", dark) ?: colors.colorBorderNeutralNormal,
        )
    }
}

@Composable
private fun ArrowButton(icon: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        DashboardIcon(icon, LocalHAColorScheme.current.colorTextPrimary, Modifier.size(HASize.X2L))
    }
}

/**
 * The picker: the presets ("Today", "Yesterday", "This week"…), the days on a calendar, and the start and end times
 * (`time-picker`). A preset is applied at once; the calendar's days with the times when selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// UPSTREAM-DIFF: Android's own date range and time pickers; upstream draws its own calendar sheet with the presets and typed times
// (the user chose native pickers for fields)
private fun DateRangeDialog(
    range: DateRange,
    hass: HassSnapshot,
    onDismiss: () -> Unit,
    onSelect: (DateRange) -> Unit,
) {
    val zone = hass.formats.zone
    val startAt = range.start.atZone(zone)
    val endAt = range.end.atZone(zone)
    // The calendar works in UTC days
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startAt.toLocalDate().utcMillis(),
        initialSelectedEndDateMillis = endAt.toLocalDate().utcMillis(),
    )
    var startTime by remember { mutableStateOf(startAt.toLocalTime()) }
    var endTime by remember { mutableStateOf(endAt.toLocalTime()) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            HAPlainButton(hass.localize("$PICKER.select"), {
                val start = state.selectedStartDateMillis?.utcDate() ?: return@HAPlainButton
                val end = state.selectedEndDateMillis?.utcDate() ?: start
                onSelect(
                    DateRange(
                        start.atTime(startTime).atZone(zone).toInstant(),
                        end.atTime(endTime).atZone(zone).toInstant(),
                    ),
                )
            })
        },
        dismissButton = { HAPlainButton(hass.localize("ui.common.cancel"), onDismiss) },
    ) {
        Column {
            DateRangePicker(state = state, modifier = Modifier.weight(1f), title = null, showModeToggle = false)
            Row(Modifier.padding(HADimens.SPACE4), horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                val ok = hass.localize("ui.common.ok")
                TimeButton(hass.localize("$PICKER.time_from"), ok, startTime, Modifier.weight(1f)) { startTime = it }
                TimeButton(hass.localize("$PICKER.time_to"), ok, endTime, Modifier.weight(1f)) { endTime = it }
            }
        }
    }
}

/** A time of the picker, as a field opening the clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(
    label: String,
    okLabel: String,
    time: LocalTime,
    modifier: Modifier,
    onPick: (LocalTime) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        HATextField(
            value = "%02d:%02d".format(java.util.Locale.ROOT, time.hour, time.minute),
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(role = Role.Button) { open = true })
    }
    if (!open) return
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    AlertDialog(
        onDismissRequest = { open = false },
        text = { TimePicker(state = picker) },
        confirmButton = {
            HAPlainButton(okLabel, {
                open = false
                // The end of a minute keeps its last millisecond, as a whole day's end has
                onPick(
                    LocalTime.of(picker.hour, picker.minute).let {
                        if (it ==
                            time.withSecond(0).withNano(0)
                        ) {
                            time
                        } else {
                            it
                        }
                    },
                )
            })
        },
    )
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val LABEL_SIZE = 12.sp
private const val PICKER = "ui.components.date-range-picker"
