package io.homeassistant.companion.android.dashboard.ui.components.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.FieldSelector
import io.homeassistant.companion.android.dashboard.ui.components.haSliderColors
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// The editors of a script's fields: ports of the `ha-selector-*` the details draw natively (frontend@20260624.6
// src/components/ha-selector/). A value cleared to nothing leaves the data, as `ha-service-control` drops it.

/**
 * The editor of [selector] for [value]; [onChange] gets the new value, `null` to leave it out. A date and time's
 * time picked first is on [today].
 *
 * Upstream: `ha-selector` (src/components/ha-selector/ha-selector.ts)
 */
@Composable
internal fun FieldEditor(
    selector: FieldSelector,
    value: JsonElement?,
    enabled: Boolean,
    placeholder: String?,
    today: LocalDate,
    onChange: (JsonElement?) -> Unit,
) {
    val text = value.textValue()
    when (selector) {
        is FieldSelector.Text -> TextEditor(selector, text, enabled, placeholder, onChange)
        is FieldSelector.Number -> NumberEditor(selector, (value as? JsonPrimitive)?.doubleOrNull, enabled, onChange)
        FieldSelector.Toggle -> HASwitch(
            checked = (value as? JsonPrimitive)?.booleanOrNull == true,
            onCheckedChange = { onChange(JsonPrimitive(it)) },
            enabled = enabled,
        )
        is FieldSelector.Select -> SelectEditor(selector.options, selector.multiple, value, enabled, onChange)
        is FieldSelector.Entity -> SelectEditor(
            selector.options,
            selector.multiple,
            value,
            enabled,
            onChange,
            dropdown = true,
        )
        FieldSelector.Date -> DateEditor(text, enabled) { onChange(JsonPrimitive(it)) }
        FieldSelector.Time -> TimeEditor(text, enabled) { onChange(JsonPrimitive(it)) }
        FieldSelector.DateTime -> DateTimeEditor(text, enabled, today, onChange)
        is FieldSelector.Duration -> DurationEditor(value, enabled, selector.enableDay, onChange)
        is FieldSelector.Constant -> Text(selector.label ?: selector.value.toString(), style = Css.Text.Document)
        is FieldSelector.Unsupported -> UnsupportedEditor(text ?: value?.toString().orEmpty(), enabled, onChange)
    }
}

// UPSTREAM-DIFF: selectors not drawn natively (areas, devices, colours, locations...) are edited as their text, sent
// as typed; upstream draws each selector's own picker
/**
 * The value of a selector not drawn natively, as its text.
 *
 * Upstream: none (native: text fallback for selectors without a native editor)
 */
@Composable
private fun UnsupportedEditor(text: String, enabled: Boolean, onChange: (JsonElement?) -> Unit) {
    HATextField(
        value = text,
        onValueChange = { onChange(it.takeIf(String::isNotEmpty)?.let(::JsonPrimitive)) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * `ha-selector-text`: a field, on several lines or hidden as asked.
 *
 * Upstream: `ha-selector-text` (src/components/ha-selector/ha-selector-text.ts)
 */
@Composable
private fun TextEditor(
    selector: FieldSelector.Text,
    text: String?,
    enabled: Boolean,
    placeholder: String?,
    onChange: (JsonElement?) -> Unit,
) {
    HATextField(
        value = text.orEmpty(),
        onValueChange = { onChange(it.takeIf(String::isNotEmpty)?.let(::JsonPrimitive)) },
        enabled = enabled,
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = !selector.multiline,
        visualTransformation = if (selector.password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = selector.suffix?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * `ha-selector-number`: a slider with the value in a box beside it, or the box alone.
 *
 * Upstream: `ha-selector-number` (src/components/ha-selector/ha-selector-number.ts)
 */
@Composable
private fun NumberEditor(
    selector: FieldSelector.Number,
    value: Double?,
    enabled: Boolean,
    onChange: (JsonElement?) -> Unit,
) {
    var typed by remember(value) { mutableStateOf(value?.let(::numberText).orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        val min = selector.min
        val max = selector.max
        if (!selector.box && min != null && max != null) {
            Slider(
                value = (value ?: min).toFloat(),
                onValueChange = { raw ->
                    val step = selector.step ?: 1.0
                    onChange(JsonPrimitive(numberValue(min + Math.round((raw - min) / step) * step)))
                },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                colors = haSliderColors(),
                modifier = Modifier.weight(1f),
            )
        }
        HATextField(
            value = typed,
            onValueChange = { input ->
                typed = input
                onChange(input.toDoubleOrNull()?.let { JsonPrimitive(numberValue(it)) })
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = selector.unit?.let { { Text(it) } },
            modifier = if (selector.box) Modifier.fillMaxWidth() else Modifier.widthIn(max = NUMBER_WIDTH),
        )
    }
}

/**
 * `ha-selector-select`: radio buttons for one of a few options, a menu for more, boxes for several.
 *
 * Upstream: `ha-selector-select`, `ha-selector-entity` (src/components/ha-selector/ha-selector-select.ts;
 * src/components/ha-selector/ha-selector-entity.ts)
 */
@Composable
private fun SelectEditor(
    options: List<Pair<String, String>>,
    multiple: Boolean,
    value: JsonElement?,
    enabled: Boolean,
    onChange: (JsonElement?) -> Unit,
    dropdown: Boolean = false,
) {
    if (multiple) {
        val selected = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        Column {
            options.forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HACheckbox(
                        checked = key in selected,
                        onCheckedChange = { on ->
                            val next = if (on) selected + key else selected - key
                            onChange(next.takeIf { it.isNotEmpty() }?.let { JsonArray(it.map(::JsonPrimitive)) })
                        },
                        enabled = enabled,
                    )
                    Text(label, style = Css.Text.Document, color = LocalHAColorScheme.current.colorTextPrimary)
                }
            }
        }
        return
    }
    val current = (value as? JsonPrimitive)?.content
    if (dropdown || options.size > LIST_MAX_OPTIONS) {
        HADropdownMenu(
            items = options.map { (key, label) -> HADropdownItem(key, label) },
            selectedKey = current,
            onItemSelected = { onChange(JsonPrimitive(it)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        HARadioGroup(
            options = options.map { (key, label) -> RadioOption(key, label, enabled = enabled) },
            onSelect = { onChange(JsonPrimitive(it.selectionKey)) },
            selectionKey = current,
            spaceBy = Css.Space.S2,
        )
    }
}

/**
 * `ha-selector-duration`: days (when enabled), hours, minutes and seconds, as an object of them.
 *
 * Upstream: `ha-selector-duration` (src/components/ha-selector/ha-selector-duration.ts)
 */
@Composable
private fun DurationEditor(
    value: JsonElement?,
    enabled: Boolean,
    enableDay: Boolean,
    onChange: (JsonElement?) -> Unit,
) {
    val parts = DURATION_PARTS.filter { enableDay || it != "days" }
    val current = (value as? JsonObject).orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        parts.forEach { part ->
            HATextField(
                value = (current[part] as? JsonPrimitive)?.content.orEmpty(),
                onValueChange = { input ->
                    val next = current + (part to JsonPrimitive(input.toIntOrNull() ?: 0))
                    onChange(JsonObject(next))
                },
                enabled = enabled,
                singleLine = true,
                label = { Text(part) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A value as text: a string or number, `null` for a switch's or none. */
private fun JsonElement?.textValue(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString || it.booleanOrNull == null }?.content

/** A whole number without its ".0", as a number box shows it. */
private fun numberText(value: Double): String = if (value ==
    Math.rint(value)
) {
    value.toLong().toString()
} else {
    value.toString()
}

/** A whole number as an integer, else the number, as JSON sends it. */
private fun numberValue(value: Double): Number = if (value == Math.rint(value)) value.toLong() else value

private const val LIST_MAX_OPTIONS = 5
private val NUMBER_WIDTH = 96.dp
private val DURATION_PARTS = listOf("days", "hours", "minutes", "seconds")
