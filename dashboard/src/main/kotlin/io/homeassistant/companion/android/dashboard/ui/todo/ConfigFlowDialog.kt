package io.homeassistant.companion.android.dashboard.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.withFallback
import io.homeassistant.companion.android.dashboard.flow.FLOW
import io.homeassistant.companion.android.dashboard.flow.FlowField
import io.homeassistant.companion.android.dashboard.flow.FlowFieldType
import io.homeassistant.companion.android.dashboard.flow.FlowFormTexts
import io.homeassistant.companion.android.dashboard.flow.FlowStep
import io.homeassistant.companion.android.dashboard.flow.abortText
import io.homeassistant.companion.android.dashboard.flow.createdTexts
import io.homeassistant.companion.android.dashboard.flow.formTexts
import io.homeassistant.companion.android.dashboard.flow.initialTexts
import io.homeassistant.companion.android.dashboard.flow.values
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import kotlinx.serialization.json.JsonObject

/**
 * Port of the data entry flow dialog (frontend@20260624.6 src/dialogs/config-flow/) for a config flow: the step's
 * form with its errors, then that the entry was created ("Finish"), or why the flow ended ("Close"). Closing before
 * the end drops the flow.
 *
 * Upstream: `dialog-data-entry-flow` (src/dialogs/config-flow/dialog-data-entry-flow.ts)
 */
@Composable
internal fun ConfigFlowDialog(flow: ListFlow, localize: Localize, onSubmit: (JsonObject) -> Unit, onClose: () -> Unit) {
    val texts =
        (flow as? ListFlow.Step)?.let { remember(localize, it.translations) { localize.withFallback(it.translations) } }
            ?: localize
    when (flow) {
        ListFlow.Loading -> FlowAlert(title = null, onClose = onClose, closeLabel = localize("ui.common.cancel")) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HALoading() }
        }
        is ListFlow.Failed -> FlowAlert(localize("$FLOW.error"), onClose, localize("$FLOW.close")) {
            Text("${localize("$FLOW.could_not_load")}: ${LocalContext.current.loadErrorText(flow.error)}")
        }
        is ListFlow.Step -> when (val step = flow.step) {
            is FlowStep.Form -> FormStep(step, flow, texts, onSubmit, onClose)
            is FlowStep.CreateEntry -> FlowAlert(texts("$FLOW.success"), onClose, texts("$FLOW.finish")) {
                Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                    texts.createdTexts(step).forEach {
                        Text(it, style = HATextStyle.Body.copy(textAlign = TextAlign.Start))
                    }
                }
            }
            is FlowStep.Abort -> FlowAlert(texts("$FLOW.aborted"), onClose, texts("$FLOW.close")) {
                Text(texts.abortText(step), style = HATextStyle.Body.copy(textAlign = TextAlign.Start))
            }
        }
    }
}

/**
 * A step's form: its description, its fields, its errors, and its submit button.
 *
 * Upstream: `step-flow-form` (src/dialogs/config-flow/step-flow-form.ts)
 */
@Composable
private fun FormStep(
    step: FlowStep.Form,
    flow: ListFlow.Step,
    localize: Localize,
    onSubmit: (JsonObject) -> Unit,
    onClose: () -> Unit,
) {
    val texts = remember(step, localize) { localize.formTexts(step) }
    var typed by remember(step.flowId, step.stepId) { mutableStateOf(step.initialTexts()) }
    var missing by remember(step) { mutableStateOf(false) }
    val danger = LocalHAColorScheme.current.colorOnDangerNormal
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(texts.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            ) {
                texts.description?.let { Text(it, style = HATextStyle.Body.copy(textAlign = TextAlign.Start)) }
                val base = texts.errors["base"]
                    ?: localize("$FLOW.not_all_required_fields").takeIf { missing }
                    ?: flow.failure?.let { "${localize("$FLOW.error")}: ${LocalContext.current.loadErrorText(it)}" }
                base?.let { Text(it, color = danger, style = HATextStyle.Body.copy(textAlign = TextAlign.Start)) }
                step.fields.forEach { field ->
                    FlowFieldInput(field, texts, typed[field.name].orEmpty(), enabled = !flow.submitting) {
                        typed = typed + (field.name to it)
                    }
                }
            }
        },
        confirmButton = {
            HAPlainButton(texts.submit, {
                val values = step.values(typed)
                missing = values == null
                values?.let(onSubmit)
            }, enabled = !flow.submitting)
        },
        dismissButton = { HAPlainButton(localize("ui.common.cancel"), onClose) },
    )
}

/**
 * A field as its type is filled in, with its help and its error under it.
 *
 * Upstream: part of `ha-form` (src/components/ha-form/ha-form.ts)
 */
@Composable
private fun FlowFieldInput(
    field: FlowField,
    texts: FlowFormTexts,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    // Required fields are marked with a star, as `ha-form` does
    val label = (texts.labels[field.name] ?: field.name) + if (field.required) "*" else ""
    val colors = LocalHAColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
        when (val type = field.type) {
            FlowFieldType.Toggle -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = HATextStyle.Body.copy(textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
                HASwitch(checked = value == "true", onCheckedChange = { onChange(it.toString()) }, enabled = enabled)
            }
            is FlowFieldType.Select -> HADropdownMenu(
                items = type.options.map { (key, text) -> HADropdownItem(key, text) },
                selectedKey = value.ifEmpty { null },
                onItemSelected = onChange,
                label = label,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            is FlowFieldType.Number, is FlowFieldType.Text -> HATextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                enabled = enabled,
                singleLine = true,
                isError = field.name in texts.errors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        type is FlowFieldType.Number -> KeyboardType.Number
                        (type as FlowFieldType.Text).password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                visualTransformation = if ((type as? FlowFieldType.Text)?.password == true) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        texts.helpers[field.name]?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
        }
        texts.errors[field.name]?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorOnDangerNormal,
            )
        }
    }
}

/**
 * A dialog with [title], [content] and one button that closes it.
 *
 * Upstream: `step-flow-create-entry`, `step-flow-abort` (src/dialogs/config-flow/step-flow-create-entry.ts;
 * src/dialogs/config-flow/step-flow-abort.ts)
 */
@Composable
private fun FlowAlert(title: String?, onClose: () -> Unit, closeLabel: String, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = title?.let { { Text(it) } },
        text = content,
        confirmButton = { HAPlainButton(closeLabel, onClose) },
    )
}
