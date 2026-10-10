package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the input rows (frontend@20260624.6 src/panels/lovelace/entity-rows/): `hui-number-entity-row` and
// `hui-input-number-entity-row` (a slider or a number box), `hui-select-entity-row` and
// `hui-input-select-entity-row` (a dropdown), and `hui-text-entity-row` and `hui-input-text-entity-row` (a text
// field). The date and time rows are in EntityRowDateTime.kt, the timer's in EntityRowTimer.kt.

/** Where a row's value goes: `[domain].set_value` with the entity as data (helpers) or as the target (entities). */
data class RowValueTarget(val domain: String, val entityId: String, val asTarget: Boolean) {
    /** The call setting [key] to [value]. */
    fun call(service: String, key: String, value: JsonPrimitive): CardAction.CallService {
        val entity = JsonObject(mapOf("entity_id" to JsonPrimitive(entityId)))
        return if (asTarget) {
            CardAction.CallService(domain, service, JsonObject(mapOf(key to value)), target = entity)
        } else {
            CardAction.CallService(domain, service, JsonObject(entity + (key to value)), target = null)
        }
    }
}

/** A number's slider, with the formatted state beside it ("—" when unknown for a number entity). */
data class RowSlider(
    val value: Double?,
    val min: Double,
    val max: Double,
    val step: Double,
    val state: String,
    val enabled: Boolean,
    val service: ValueService,
) : RowControl

/** A number box with its unit; [value] is upstream's text of the state. */
data class RowNumberBox(
    val value: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
    val unit: String?,
    val enabled: Boolean,
    val target: RowValueTarget,
) : RowControl {
    /** The call setting the number typed. */
    fun set(text: String): CardAction.CallService = target.call("set_value", "value", JsonPrimitive(text))
}

/** A dropdown labelled with the entity's name, in place of it. */
data class RowSelect(
    val label: String,
    val value: String?,
    val options: List<Pair<String, String>>,
    val enabled: Boolean,
    val target: RowValueTarget,
) : RowControl {
    /** The call choosing [option]. */
    fun choose(option: String): CardAction.CallService = target.call("select_option", "option", JsonPrimitive(option))
}

/** A text field labelled with the entity's name, in place of it. */
data class RowTextInput(
    val label: String,
    val value: String,
    val minLength: Int?,
    val maxLength: Int?,
    val pattern: String?,
    val password: Boolean,
    val placeholder: String,
    val enabled: Boolean,
    val target: RowValueTarget,
) : RowControl {
    /** The call setting [text], or `null` for one that would read as a state ("unavailable", "unknown"). */
    fun set(text: String): CardAction.CallService? =
        target.call("set_value", "value", JsonPrimitive(text)).takeUnless { text in INVALID_TEXTS }
}

/** The input row of [state], `null` for a domain without one. */
internal fun HassSnapshot.inputRowControl(state: EntityState, name: String): RowControl? = when (state.domain) {
    "input_number", "number" -> numberRow(state)
    "input_select", "select" -> selectRow(state, name)
    "input_text", "text" -> textRow(state, name)
    else -> dateTimeRowControl(state, name) ?: timerRowControl(state)
}

private fun HassSnapshot.numberRow(state: EntityState): RowControl {
    val helper = state.domain == "input_number"
    val attributes = state.attributes
    val min = number(attributes["min"])
    val max = number(attributes["max"])
    val step = number(attributes["step"])
    val available = state.state != STATE_UNAVAILABLE
    return if (showsSlider(state, min, max, step)) {
        val unknown = state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN
        RowSlider(
            value = state.state.toDoubleOrNull(),
            min = min ?: 0.0,
            max = max ?: DEFAULT_MAX,
            step = step ?: 1.0,
            state = if (unknown && !helper) "—" else formatEntityState(state),
            enabled = available,
            service = ValueService(
                state.domain,
                "set_value",
                JsonObject(
                    mapOf(
                        "entity_id" to JsonPrimitive(state.entityId),
                    ),
                ),
                "value",
            ),
        )
    } else {
        RowNumberBox(
            // Upstream's box shows "NaN" (a helper) or the raw state (an entity) when it isn't a number: shown
            // as the state instead, so an unavailable number doesn't read as a value
            value = state.state.trim().toDoubleOrNull()?.let(::jsNumberString) ?: formatEntityState(state),
            min = min,
            max = max,
            step = step,
            unit = attributes.string("unit_of_measurement")?.ifEmpty { null },
            enabled = available,
            target = RowValueTarget(state.domain, state.entityId, asTarget = false),
        )
    }
}

/** A slider when asked for, or for a number entity on "auto" with at most 256 steps. */
private fun showsSlider(state: EntityState, min: Double?, max: Double?, step: Double?): Boolean {
    val mode = state.attributes.string("mode")
    val fewSteps = min != null && max != null && step != null && (max - min) / step <= AUTO_SLIDER_STEPS
    return mode == "slider" || (state.domain == "number" && mode == "auto" && fewSteps)
}

private fun HassSnapshot.selectRow(state: EntityState, name: String): RowSelect {
    val helper = state.domain == "input_select"
    val options = (state.attributes["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    return RowSelect(
        label = name,
        value = state.state,
        // A select entity's options are translated as its states, a helper's are shown as they are
        options = options.map { it to if (helper) it else formatEntityState(state, it) },
        enabled = state.state != STATE_UNAVAILABLE,
        target = RowValueTarget(state.domain, state.entityId, asTarget = !helper),
    )
}

private fun HassSnapshot.textRow(state: EntityState, name: String): RowTextInput = RowTextInput(
    label = name,
    value = state.state,
    minLength = number(state.attributes["min"])?.toInt(),
    maxLength = number(state.attributes["max"])?.toInt(),
    pattern = state.attributes.string("pattern")?.ifEmpty { null },
    password = state.attributes.string("mode") == "password",
    placeholder = localize("ui.card.text.empty_value"),
    enabled = state.state != STATE_UNAVAILABLE,
    target = RowValueTarget(state.domain, state.entityId, asTarget = state.domain == "text"),
)

private const val AUTO_SLIDER_STEPS = 256
private const val DEFAULT_MAX = 100.0
private val INVALID_TEXTS = setOf(STATE_UNAVAILABLE, STATE_UNKNOWN)
