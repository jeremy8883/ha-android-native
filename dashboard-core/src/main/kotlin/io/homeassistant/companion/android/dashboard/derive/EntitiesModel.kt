package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.action.toggleEntity
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Display-ready content of an entities card: an optional header and its rows. */
data class EntitiesModel(val title: String?, val icon: String?, val rows: List<EntityRowModel>)

/**
 * One entity row (`hui-generic-entity-row` with its domain's row element).
 *
 * @property state the formatted state, shown when the row has no control
 * @property control the row's control, `null` for a text-only row
 * @property actions the gestures of the row's name and icon (more-info by default)
 */
data class EntityRowModel(
    val entityId: String,
    val name: String,
    val icon: String?,
    val state: String?,
    val available: Boolean,
    val missing: Boolean,
    val control: RowControl?,
    val actions: ElementActions,
)

/** The control on the right of an entity row. */
sealed interface RowControl {
    /** A switch (`ha-entity-toggle`), [action] turns the entity on or off. */
    data class Toggle(val checked: Boolean, val enabled: Boolean, val action: CardAction.CallService) : RowControl

    /** Text buttons, such as "Press", "Activate", "Run" or "Unlock". */
    data class Buttons(val buttons: List<RowButton>) : RowControl
}

/** A text button of an entity row. */
data class RowButton(val label: String, val enabled: Boolean, val danger: Boolean, val action: CardAction.CallService)

/**
 * Derive an entities card. Each entry is an entity id or a row config; non-entity rows (dividers, sections, ...)
 * are not ported yet and skipped. Ports of `hui-entities-card`, `DOMAIN_TO_ELEMENT_TYPE` and the toggle, button,
 * input button, scene, script and lock rows (frontend@20260624.6 src/panels/lovelace/cards/hui-entities-card.ts,
 * src/panels/lovelace/create-element/create-row-element.ts, src/panels/lovelace/entity-rows/). Other domains show
 * their state as text.
 */
fun HassSnapshot.entitiesModel(card: CardConfig, now: Instant): EntitiesModel = EntitiesModel(
    title = card.json.string("title")?.ifEmpty { null },
    icon = card.json.string("icon")?.ifEmpty { null },
    rows = (card.json["entities"] as? JsonArray).orEmpty().mapNotNull { entry ->
        val config = when {
            entry is JsonObject -> entry
            entry is JsonPrimitive && entry.isString -> JsonObject(mapOf("entity" to entry))
            else -> null
        }
        config?.takeIf { it.string("entity") != null }?.let { entityRow(it, now) }
    },
)

private fun HassSnapshot.entityRow(config: JsonObject, now: Instant): EntityRowModel {
    val entityId = config.string("entity").orEmpty()
    // Rows default to more-info on tap, like the tile card
    val actions = cardActions(CardConfig(JsonObject(config + ("type" to JsonPrimitive("tile"))))).card
    val state = states[entityId]
        ?: return EntityRowModel(entityId, entityId, "mdi:alert-circle", null, false, true, null, actions)
    val control = rowControl(state, config)
    return EntityRowModel(
        entityId = entityId,
        name = entityNameDisplay(state, config["name"]),
        icon = entityIcon(entityId, config.string("icon")),
        state = if (control == null) rowStateText(state, config, now) else null,
        available = state.state != STATE_UNAVAILABLE,
        missing = false,
        control = control,
        actions = actions,
    )
}

/**
 * The text of a row without a control: sensor rows show timestamps as relative times (`hui-sensor-entity-row`),
 * every other row its formatted state (`hui-simple-entity-row`).
 */
private fun HassSnapshot.rowStateText(state: EntityState, config: JsonObject, now: Instant): String =
    if (state.domain == "sensor") {
        stateDisplay(state, JsonPrimitive("state"), now, timeFormat = config.string("time_format"))
    } else {
        formatEntityState(state)
    }

private fun HassSnapshot.rowControl(state: EntityState, config: JsonObject): RowControl? {
    val available = state.state != STATE_UNAVAILABLE
    fun call(domain: String, service: String) =
        CardAction.CallService(domain, service, buildJsonObject { put("entity_id", state.entityId) }, target = null)
    fun button(label: String, action: CardAction.CallService, enabled: Boolean = available, danger: Boolean = false) =
        RowControl.Buttons(listOf(RowButton(label, enabled, danger, action)))
    val actionName = config.string("action_name")?.ifEmpty { null }
    return when (DOMAIN_ROWS[state.domain]) {
        ROW_TOGGLE -> if (state.state in TOGGLE_STATES) {
            RowControl.Toggle(state.state == "on", available, toggleEntity(state.entityId))
        } else {
            null
        }
        ROW_BUTTON -> button(localize("ui.card.button.press"), call("button", "press"))
        ROW_INPUT_BUTTON -> button(localize("ui.card.button.press"), call("input_button", "press"))
        ROW_SCENE -> button(actionName ?: localize("ui.card.scene.activate"), call("scene", "turn_on"))
        ROW_SCRIPT -> scriptButtons(state, actionName, ::call)
        ROW_LOCK -> {
            val service = if (state.state == "locked") "unlock" else "lock"
            val code = state.attributes.string("code_format")?.ifEmpty { null }?.let {
                CodeRequest(state.entityId, "lock", codeFormat = "text", title = localize("ui.card.lock.$service"))
            }
            button(localize("ui.card.lock.$service"), call("lock", service).copy(code = code))
        }
        else -> null
    }
}

/** Port of the script row: cancel while running, run when off or when more runs are allowed. */
private fun HassSnapshot.scriptButtons(
    state: EntityState,
    actionName: String?,
    call: (String, String) -> CardAction.CallService,
): RowControl.Buttons {
    val attributes = state.attributes
    val current = (attributes["current"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
    val max = (attributes["max"] as? JsonPrimitive)?.content?.toDoubleOrNull()
    val buttons = mutableListOf<RowButton>()
    if (state.state == "on") {
        val label = if (attributes.string("mode") != "single" && current > 0) {
            localize("ui.card.script.cancel_multiple", mapOf("number" to current.toLong().toString()))
        } else {
            localize("ui.card.script.cancel")
        }
        buttons += RowButton(label, enabled = true, danger = true, action = call("script", "turn_off"))
    }
    if (state.state == "off" || jsTruthy(attributes["max"])) {
        // Port of `canRun` (src/data/script.ts): queued and parallel scripts run again until their `max` runs
        val canRun = state.state == "off" ||
            (state.state == "on" && attributes.string("mode") in MAX_MODES && max != null && current < max)
        buttons += RowButton(
            actionName ?: localize("ui.card.script.run"),
            enabled = state.state != STATE_UNAVAILABLE && canRun,
            danger = false,
            action = call("script", "turn_on"),
        )
    }
    return RowControl.Buttons(buttons)
}

private const val ROW_TOGGLE = "toggle"
private const val ROW_BUTTON = "button"
private const val ROW_INPUT_BUTTON = "input-button"
private const val ROW_SCENE = "scene"
private const val ROW_SCRIPT = "script"
private const val ROW_LOCK = "lock"
private val MAX_MODES = setOf("queued", "parallel")
private val TOGGLE_STATES = setOf("on", "off", STATE_UNAVAILABLE, STATE_UNKNOWN)

/** The ported part of `DOMAIN_TO_ELEMENT_TYPE`; other domains render as text rows. */
private val DOMAIN_ROWS = mapOf(
    "alert" to ROW_TOGGLE, "automation" to ROW_TOGGLE, "fan" to ROW_TOGGLE, "input_boolean" to ROW_TOGGLE,
    "light" to ROW_TOGGLE, "remote" to ROW_TOGGLE, "siren" to ROW_TOGGLE, "switch" to ROW_TOGGLE,
    "vacuum" to ROW_TOGGLE, "button" to ROW_BUTTON, "input_button" to ROW_INPUT_BUTTON, "scene" to ROW_SCENE,
    "script" to ROW_SCRIPT, "lock" to ROW_LOCK,
)
