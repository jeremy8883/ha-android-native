package io.homeassistant.companion.android.dashboard.flow

import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// A config flow (`config/config_entries/flow`) as the frontend's data entry flow dialog shows it: its steps, their
// forms and their texts. Ports of `DataEntryFlowStep` (frontend@20260624.6 src/data/data_entry_flow.ts) and of the
// texts `showConfigFlowDialog` gives the dialog (src/dialogs/config-flow/show-dialog-config-flow.ts).

/** A step of a config flow for [handler] (an integration's domain). */
sealed interface FlowStep {
    val flowId: String
    val handler: String

    /**
     * A form to fill in.
     *
     * @property errors the error key of each field that was refused, `base` for the whole form
     * @property lastStep whether submitting it finishes the flow; `null` when the integration doesn't say
     */
    data class Form(
        override val flowId: String,
        override val handler: String,
        val stepId: String,
        val translationDomain: String?,
        val fields: List<FlowField>,
        val errors: Map<String, String>,
        val placeholders: Map<String, String>,
        val lastStep: Boolean?,
    ) : FlowStep

    /** The config entry [entryId] was created, named [title]; [notLoaded] when it couldn't be set up. */
    data class CreateEntry(
        override val flowId: String,
        override val handler: String,
        val title: String,
        val entryId: String?,
        val description: String?,
        val translationDomain: String?,
        val placeholders: Map<String, String>,
        val notLoaded: Boolean,
    ) : FlowStep

    /** The flow ended without an entry, for [reason] (such as `already_configured`). */
    data class Abort(
        override val flowId: String,
        override val handler: String,
        val reason: String,
        val translationDomain: String?,
        val placeholders: Map<String, String>,
    ) : FlowStep
}

/** A field of a form, from its `data_schema`, with its [default] value. */
data class FlowField(val name: String, val required: Boolean, val type: FlowFieldType, val default: JsonElement?)

/** How a field is filled in. */
sealed interface FlowFieldType {
    /** Text, hidden when [password]. */
    data class Text(val password: Boolean = false) : FlowFieldType

    data object Toggle : FlowFieldType

    /** One of [options], as (value, label). */
    data class Select(val options: List<Pair<String, String>>) : FlowFieldType

    /** A number, whole when [integer]. */
    data class Number(val integer: Boolean) : FlowFieldType
}

/** A step from the server's answer, `null` when it is none the dialog knows. */
fun parseFlowStep(json: JsonObject): FlowStep? {
    val flowId = json.string("flow_id")
    val handler = json.string("handler")
    if (flowId == null || handler == null) return null
    val placeholders = (json["description_placeholders"] as? JsonObject).strings()
    val domain = json.string("translation_domain")
    return when (json.string("type")) {
        "form" -> json.string("step_id")?.let { stepId ->
            FlowStep.Form(
                flowId = flowId,
                handler = handler,
                stepId = stepId,
                translationDomain = domain,
                fields = (json["data_schema"] as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonObject)?.let(::parseField)
                },
                errors = (json["errors"] as? JsonObject).strings(),
                placeholders = placeholders,
                lastStep = json.boolean("last_step"),
            )
        }
        "create_entry" -> FlowStep.CreateEntry(
            flowId = flowId,
            handler = handler,
            title = json.string("title").orEmpty(),
            entryId = json.obj("result")?.string("entry_id"),
            description = json.string("description"),
            translationDomain = domain,
            placeholders = placeholders,
            notLoaded = json.obj("result")?.string("state") == "not_loaded",
        )
        "abort" -> FlowStep.Abort(flowId, handler, json.string("reason").orEmpty(), domain, placeholders)
        else -> null
    }
}

/** A field of a `data_schema` (voluptuous-serialize's types, or a `selector`), `null` for ones not drawn natively. */
private fun parseField(field: JsonObject): FlowField? {
    val name = field.string("name")
    val type = field.obj("selector")?.let(::selectorType) ?: schemaType(field)
    return if (name != null &&
        type != null
    ) {
        FlowField(name, field.boolean("required") == true, type, field["default"])
    } else {
        null
    }
}

/** A voluptuous-serialize type's field. */
private fun schemaType(field: JsonObject): FlowFieldType? = when (field.string("type")) {
    "string" -> FlowFieldType.Text(password = field.string("format") == "password")
    "boolean" -> FlowFieldType.Toggle
    "integer" -> FlowFieldType.Number(integer = true)
    "float" -> FlowFieldType.Number(integer = false)
    "select" -> selectOptions(field["options"])?.let { FlowFieldType.Select(it) }
    else -> null
}

private fun selectorType(selector: JsonObject): FlowFieldType? = when {
    "text" in selector -> FlowFieldType.Text(password = selector.obj("text")?.string("type") == "password")
    "boolean" in selector -> FlowFieldType.Toggle
    "number" in selector -> FlowFieldType.Number(integer = (selector.obj("number")?.number("step") ?: 1.0) % 1.0 == 0.0)
    "select" in selector -> selectOptions(selector.obj("select")?.get("options"))?.let { FlowFieldType.Select(it) }
    else -> null
}

/** Options as `[[value, label], ...]`, `{value: label}`, `[value, ...]` or `[{value, label}, ...]`. */
private fun selectOptions(options: JsonElement?): List<Pair<String, String>>? = when (options) {
    is JsonArray -> options.mapNotNull { option ->
        when (option) {
            is JsonArray -> option.getOrNull(0)?.stringOrNull?.let { it to (option.getOrNull(1)?.stringOrNull ?: it) }
            is JsonObject -> option.string("value")?.let { it to (option.string("label") ?: it) }
            is JsonPrimitive -> option.stringOrNull?.let { it to it }
        }
    }
    is JsonObject -> options.mapNotNull { (value, label) -> label.stringOrNull?.let { value to it } }
    else -> null
}

private fun JsonObject?.strings(): Map<String, String> =
    this?.mapNotNull { (key, value) -> value.stringOrNull?.let { key to it } }?.toMap().orEmpty()

/**
 * The values to submit: each field's typed text as its type (a number, a boolean), left out when blank; `null` when
 * a required field is blank, which the dialog says as `not_all_required_fields`.
 */
fun FlowStep.Form.values(typed: Map<String, String>): JsonObject? {
    val values = fields.mapNotNull { field ->
        val text = typed[field.name]?.trim().orEmpty()
        when {
            text.isEmpty() && field.required && field.type !is FlowFieldType.Toggle -> return null
            text.isEmpty() -> null
            else -> field.name to field.value(text)
        }
    }
    return JsonObject(values.toMap())
}

/** The fields' starting texts, from their defaults. */
fun FlowStep.Form.initialTexts(): Map<String, String> = fields.mapNotNull { field ->
    field.default?.let { (it as? JsonPrimitive)?.content }?.let { field.name to it }
}.toMap()

private fun FlowField.value(text: String): JsonElement = when (val kind = type) {
    FlowFieldType.Toggle -> JsonPrimitive(text.toBooleanStrictOrNull() ?: false)
    is FlowFieldType.Number -> if (kind.integer) {
        text.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
    } else {
        text.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
    }
    else -> JsonPrimitive(text)
}
