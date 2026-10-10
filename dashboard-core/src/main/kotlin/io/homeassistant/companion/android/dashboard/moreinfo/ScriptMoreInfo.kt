package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of `more-info-script` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-script.ts) with
 * `canRun` and the required fields' checks (src/data/script.ts): the state header ("Idle", "Running" or how many
 * run at once, and when it last ran), the script's description, how many runs are queued, its fields as
 * `ha-service-control` draws them, and cancel and run.
 *
 * @property fields the fields, `null` when it has none
 * @property initialData the fields' values as the form opens: their defaults, and `false` for required switches
 */
data class ScriptMoreInfo(
    val state: String,
    val changed: String,
    val description: String?,
    val queue: String?,
    val fieldsTitle: String,
    val fields: List<ScriptField>?,
    val initialData: Map<String, JsonElement>,
    val cancel: String,
    val cancelEnabled: Boolean,
    val run: String,
    private val scriptId: String,
    private val entityId: String,
    private val available: Boolean,
    private val canRunNow: Boolean,
    private val restart: Boolean,
) {
    /** Whether it can run with the form's [data]: required fields filled (unless it restarts), and not running. */
    fun canRun(data: Map<String, JsonElement>): Boolean {
        if (!available) return false
        val required = fields.orEmpty().filter { it.required }
        return required.isEmpty() || (required.all { it.key in data } && canRunNow) || restart
    }

    /** Run it with the form's [data]: `script.<id>` with the data as its variables. */
    fun runCall(data: Map<String, JsonElement>): CardAction.CallService =
        CardAction.CallService(SCRIPT, scriptId, JsonObject(data), target = null)

    /** Cancel its runs. */
    fun cancelCall(): CardAction.CallService = CardAction.CallService(
        SCRIPT,
        "turn_off",
        JsonObject(mapOf("entity_id" to JsonPrimitive(entityId))),
        target = null,
    )
}

/**
 * A field of the form: its name and description, how it's edited ([selector]), whether it's [required], and
 * whether it has the box that includes it ([optional]; an optional field is sent only when ticked).
 */
data class ScriptField(
    val key: String,
    val name: String,
    val description: String?,
    val selector: FieldSelector,
    val required: Boolean,
    val optional: Boolean,
    val default: JsonElement?,
) {
    /** The value ticking the box puts in: the default, a constant's value, or `false` for a switch. */
    fun tickedValue(): JsonElement? = default ?: (selector as? FieldSelector.Constant)?.value
        ?: JsonPrimitive(false).takeIf { selector is FieldSelector.Toggle }
}

/**
 * The script details of [state] at [now], its action [scriptId] being its registry entry's unique ID (upstream's
 * `services.script` key), else its object ID; `null` without the services list.
 */
fun HassSnapshot.scriptMoreInfo(
    state: EntityState,
    now: Instant,
    scriptId: String = state.entityId.substringAfter('.'),
): ScriptMoreInfo? {
    val service = services?.get(SCRIPT)?.get(scriptId)
    val attributes = state.attributes
    val current = number(attributes["current"])?.toLong() ?: 0L
    val mode = attributes.string("mode")
    val several = mode in MAX_MODES && current > 1
    val fields = (service?.get("fields") as? JsonObject)?.mapNotNull { (key, field) ->
        (field as? JsonObject)?.let { scriptField(key, it) }?.let(::withEntityOptions)
    }?.takeIf { it.isNotEmpty() }
    return ScriptMoreInfo(
        state = scriptState(current, mode),
        changed = lastRun(state, now),
        description = service?.string("description")?.ifEmpty { null },
        queue = localize("ui.card.script.running_queued", mapOf("queued" to "${current - 1}")).takeIf {
            mode == "queued" &&
                current > 1
        },
        fieldsTitle = localize("ui.card.script.run_script"),
        fields = fields,
        initialData = initialData(fields.orEmpty()),
        cancel = localize(if (several) "ui.card.script.cancel_all" else "ui.card.script.cancel"),
        cancelEnabled = current > 0,
        run = localize("ui.card.script.run"),
        scriptId = scriptId,
        entityId = state.entityId,
        available = state.state != "unavailable",
        canRunNow = canRun(state, mode, current),
        restart = mode == "restart",
    )
}

/** The header's `stateOverride`: how many run at once, "Running" or "Idle". */
private fun HassSnapshot.scriptState(current: Long, mode: String?): String = when {
    current > 1 && mode == "parallel" -> localize("ui.card.script.running_parallel", mapOf("active" to "$current"))
    current > 0 -> localize("ui.card.script.running_single")
    else -> localize("ui.card.script.idle")
}

/** The header's `changedOverride`: when it last ran, "Never" when it hasn't. */
private fun HassSnapshot.lastRun(state: EntityState, now: Instant): String =
    state.attributes.string("last_triggered")?.let { parseJsDate(it, ZoneOffset.UTC) }
        ?.let { formats.relativeTime(minOf(it, now), now).replaceFirstChar { c -> c.uppercaseChar() } }
        ?: localize("ui.components.relative_time.never")

/** The form's values as it opens: the defaults, and `false` for required switches without one. */
private fun initialData(fields: List<ScriptField>): Map<String, JsonElement> = fields.mapNotNull { field ->
    when {
        field.default != null -> field.key to field.default
        field.required && field.selector is FieldSelector.Toggle -> field.key to JsonPrimitive(false)
        else -> null
    }
}.toMap()

/** Port of `canRun`: off, or running with room for another run (queued and parallel scripts, up to `max`). */
private fun canRun(state: EntityState, mode: String?, current: Long): Boolean = state.state == "off" ||
    (state.state == "on" && mode in MAX_MODES && current < (number(state.attributes["max"]) ?: 0.0))

/** An entity field with the entities it can pick, by name. */
private fun HassSnapshot.withEntityOptions(field: ScriptField): ScriptField {
    val selector = field.selector as? FieldSelector.Entity ?: return field
    val options = states.values
        .filter { selector.domains.isEmpty() || it.domain in selector.domains }
        .map { it.entityId to (it.attributes.string("friendly_name") ?: it.entityId) }
        .sortedBy { it.second.lowercase() }
    return field.copy(selector = selector.copy(options = options))
}

private fun scriptField(key: String, field: JsonObject): ScriptField? {
    val selector = (field["selector"] as? JsonObject)?.let(::fieldSelector) ?: return null
    val required = field.boolean("required") == true
    val default = field["default"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }
    return ScriptField(
        key = key,
        name = field.string("name")?.ifEmpty { null } ?: key,
        description = field.string("description")?.ifEmpty { null },
        selector = selector,
        required = required,
        // `showOptionalToggle`: not required, unless a switch with a default
        optional = !required && !(selector is FieldSelector.Toggle && jsTruthy(default)),
        default = default,
    )
}

private const val SCRIPT = "script"
private val MAX_MODES = setOf("queued", "parallel")
