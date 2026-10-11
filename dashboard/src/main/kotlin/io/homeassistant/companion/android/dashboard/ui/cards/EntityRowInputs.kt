package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.RowNumberBox
import io.homeassistant.companion.android.dashboard.derive.RowSelect
import io.homeassistant.companion.android.dashboard.derive.RowSlider
import io.homeassistant.companion.android.dashboard.derive.RowTextInput
import io.homeassistant.companion.android.dashboard.derive.RowTimer
import io.homeassistant.companion.android.dashboard.ui.controls.haSliderColors
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.Instant
import kotlinx.coroutines.delay

// The input rows' controls: ports of the entity rows' `ha-slider`, `ha-input`, `ha-select` and
// `ha-timer-remaining-time` (frontend@20260624.6 src/panels/lovelace/entity-rows/). The date and time pickers are
// in EntityRowDateTime.kt.

/**
 * A number's slider, at most 200dp wide and labelled with its value while dragged, and its state; set when let go.
 *
 * Upstream: `ha-slider` in `hui-input-number-entity-row`, `hui-number-entity-row` (src/components/ha-slider.ts;
 * src/panels/lovelace/entity-rows/hui-input-number-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-number-entity-row.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RowScope.RowSliderControl(control: RowSlider, onAction: (CardAction) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val colors = haSliderColors()
    Slider(
        value = dragging ?: (control.value ?: control.min).toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { value -> onAction(control.service.withValue(control.stepped(value.toDouble()))) }
            dragging = null
        },
        valueRange = control.min.toFloat()..control.max.toFloat(),
        enabled = control.enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            Box(contentAlignment = Alignment.Center) {
                SliderDefaults.Thumb(interactionSource, colors = colors, enabled = control.enabled)
                dragging?.let { SliderTooltip(control.tooltip(it.toDouble())) }
            }
        },
        modifier = Modifier
            .weight(1f, fill = false)
            .widthIn(min = SLIDER_MIN_WIDTH, max = SLIDER_MAX_WIDTH)
            .padding(horizontal = HADimens.SPACE2),
    )
    Text(
        control.state,
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorTextPrimary,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(min = STATE_MIN_WIDTH),
    )
}

/**
 * The value above the slider's thumb while it is dragged, `ha-slider`'s `with-tooltip`.
 *
 * Upstream: part of `ha-slider` (src/components/ha-slider.ts)
 */
@Composable
private fun SliderTooltip(text: String) {
    val colors = LocalHAColorScheme.current
    val gap = with(LocalDensity.current) { TOOLTIP_GAP.roundToPx() }
    val position = remember(gap) { AboveAnchor(gap) }
    // `--ha-tooltip-background-color` falls back to `--secondary-background-color`
    val background = resolveVariable(TOOLTIP_BACKGROUND, isSystemInDarkTheme()) ?: colors.colorSurfaceDefault
    Popup(popupPositionProvider = position, properties = PopupProperties(clippingEnabled = false)) {
        Box(
            Modifier
                .background(background, RoundedCornerShape(HARadius.S))
                .padding(HADimens.SPACE2),
        ) {
            Text(text, style = HATextStyle.Body.copy(fontSize = HAFontSize.S), color = colors.colorTextPrimary)
        }
    }
}

/** Places a popup centred above its anchor, [gap] pixels away. */
private class AboveAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
        anchorBounds.top - popupContentSize.height - gap,
    )
}

/**
 * A number box with its unit, set when done or left.
 *
 * Upstream: `ha-input` in `hui-input-number-entity-row`, `hui-number-entity-row` (src/components/input/ha-input.ts;
 * src/panels/lovelace/entity-rows/hui-input-number-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-number-entity-row.ts)
 */
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

/**
 * A dropdown in place of the name.
 *
 * Upstream: `ha-select` in `hui-input-select-entity-row`, `hui-select-entity-row` (src/components/ha-select.ts;
 * src/panels/lovelace/entity-rows/hui-input-select-entity-row.ts;
 * src/panels/lovelace/entity-rows/hui-select-entity-row.ts)
 */
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

/**
 * A text field in place of the name, set when done or left; flagged while it breaks the entity's pattern.
 *
 * Upstream: `ha-input` in `hui-input-text-entity-row`, `hui-text-entity-row` (src/components/input/ha-input.ts;
 * src/panels/lovelace/entity-rows/hui-input-text-entity-row.ts; src/panels/lovelace/entity-rows/hui-text-entity-row.ts)
 */
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

/**
 * The timer's time left, counting down each second while it runs.
 *
 * Upstream: `ha-timer-remaining-time` in `hui-timer-entity-row` (src/state-display/ha-timer-remaining-time.ts;
 * src/panels/lovelace/entity-rows/hui-timer-entity-row.ts)
 */
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

/**
 * A single-line field holding its own text while edited, handing it over when done or when focus leaves.
 *
 * Upstream: `ha-input` (src/components/input/ha-input.ts)
 */
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

// `ha-slider` in the number rows: `min-width: 100px`, `max-width: 200px`; the state `min-width: 45px`
private val SLIDER_MIN_WIDTH = 100.dp
private val SLIDER_MAX_WIDTH = 200.dp
private val STATE_MIN_WIDTH = 45.dp
private val TOOLTIP_GAP = HADimens.SPACE2
private const val TOOLTIP_BACKGROUND = "secondary-background-color"

/** Spacing between a row's date and time pickers. */
internal val PICKER_GAP = HADimens.SPACE1

/**
 * Lays out a row's pickers side by side.
 *
 * Upstream: part of `hui-datetime-entity-row` (src/panels/lovelace/entity-rows/hui-datetime-entity-row.ts)
 */
@Composable
internal fun PickerRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PICKER_GAP),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
