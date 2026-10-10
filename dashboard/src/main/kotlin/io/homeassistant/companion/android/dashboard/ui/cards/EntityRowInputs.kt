package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowNumberBox
import io.homeassistant.companion.android.dashboard.derive.RowSelect
import io.homeassistant.companion.android.dashboard.derive.RowSlider
import io.homeassistant.companion.android.dashboard.derive.RowTextInput
import io.homeassistant.companion.android.dashboard.derive.RowTimer
import io.homeassistant.companion.android.dashboard.ui.controls.haSliderColors
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// The input rows' controls: ports of the entity rows' `ha-slider`, `ha-input`, `ha-select` and
// `ha-timer-remaining-time` (frontend@20260624.6 src/panels/lovelace/entity-rows/). The date and time pickers are
// in EntityRowDateTime.kt.

/** A number's slider and its state, set when let go. */
@Composable
internal fun RowScope.RowSliderControl(control: RowSlider, onAction: (CardAction) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = dragging ?: (control.value ?: control.min).toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { value ->
                val stepped = control.min + ((value - control.min) / control.step).roundToInt() * control.step
                onAction(control.service.withValue(stepped))
            }
            dragging = null
        },
        valueRange = control.min.toFloat()..control.max.toFloat(),
        enabled = control.enabled,
        colors = haSliderColors(),
        modifier = Modifier.weight(1f),
    )
    Text(control.state, style = HATextStyle.BodyMedium, color = LocalHAColorScheme.current.colorTextPrimary)
}

/** A number box with its unit, set when done or left. */
@Composable
internal fun RowNumberBoxControl(control: RowNumberBox, onAction: (CardAction) -> Unit) {
    SubmittingField(
        value = control.value,
        enabled = control.enabled,
        keyboardType = KeyboardType.Decimal,
        suffix = control.unit,
        modifier = Modifier.widthIn(max = NUMBER_WIDTH),
    ) { text -> if (text != control.value) onAction(control.set(text)) }
}

/** A dropdown in place of the name. */
@Composable
internal fun RowScope.RowSelectControl(control: RowSelect, onAction: (CardAction) -> Unit) {
    HADropdownMenu(
        items = control.options.map { (value, label) -> HADropdownItem(value, label) },
        selectedKey = control.value?.takeIf { value -> control.options.any { it.first == value } },
        onItemSelected = { option -> if (option != control.value) onAction(control.choose(option)) },
        label = control.label,
        enabled = control.enabled,
        modifier = Modifier.weight(1f),
    )
}

/** A text field in place of the name, set when done or left; flagged while it breaks the entity's pattern. */
@Composable
internal fun RowScope.RowTextControl(control: RowTextInput, onAction: (CardAction) -> Unit) {
    SubmittingField(
        value = control.value,
        enabled = control.enabled,
        label = control.label,
        placeholder = control.placeholder,
        password = control.password,
        maxLength = control.maxLength,
        pattern = control.pattern?.let { runCatching { Regex(it) }.getOrNull() },
        modifier = Modifier.weight(1f),
    ) { text -> if (text != control.value) control.set(text)?.let(onAction) }
}

/** The timer's time left, counting down each second while it runs. */
@Composable
internal fun RowTimerText(control: RowTimer, now: Instant) {
    var ticks by remember(now, control) { mutableIntStateOf(0) }
    if (control.ticking) {
        LaunchedEffect(now, control) {
            while (true) {
                delay(TICK_MS)
                ticks++
            }
        }
    }
    Text(
        control.display(now.plusSeconds(ticks.toLong())),
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorTextPrimary,
    )
}

/** A single-line field holding its own text while edited, handing it over when done or when focus leaves. */
@Composable
private fun SubmittingField(
    value: String,
    enabled: Boolean,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    password: Boolean = false,
    maxLength: Int? = null,
    pattern: Regex? = null,
    onSubmit: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    HATextField(
        value = text,
        onValueChange = { input -> text = maxLength?.let { input.take(it) } ?: input },
        enabled = enabled,
        isError = pattern != null && text.isNotEmpty() && !pattern.matches(text),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) onSubmit(text)
            focused = state.isFocused
        },
    )
}

private const val TICK_MS = 1000L
private val NUMBER_WIDTH = 140.dp

/** Spacing between a row's date and time pickers. */
internal val PICKER_GAP = HADimens.SPACE1

/** Lays out a row's pickers side by side. */
@Composable
internal fun PickerRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PICKER_GAP),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
