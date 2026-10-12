package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.action.toggleEntity
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.display.StateDisplayOptions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Display-ready content of an entities card: an optional header (with the switch turning its entities on or off,
 * [headerToggle]) and its rows.
 */
data class EntitiesModel(
    val title: String?,
    val icon: String?,
    val rows: List<EntitiesRow>,
    val headerToggle: HeaderToggle? = null,
    val header: HeaderFooterModel? = null,
    val footer: HeaderFooterModel? = null,
) {
    /** The rows that show an entity. */
    val entityRows: List<EntityRowModel> get() = rows.filterIsInstance<EntitiesRow.Entity>().map { it.row }
}

/**
 * Port of `hui-entities-toggle`: on while any of its entities is, and [calls] turn the ones that aren't yet on
 * (or off) together (`turnOnOffEntities`: covers and locks with their own actions, the others with
 * `homeassistant`).
 */
data class HeaderToggle(val checked: Boolean, private val entities: List<EntityState>) {
    /** The calls turning the entities on (or off, when [turnOn] is false). */
    fun calls(turnOn: Boolean): List<CardAction.CallService> = entities
        .filter { (it.state in STATES_OFF_TOGGLE) == turnOn }
        .groupBy { if (it.domain == "cover" || it.domain == "lock") it.domain else "homeassistant" }
        .map { (domain, members) ->
            val service = when (domain) {
                "lock" -> if (turnOn) "unlock" else "lock"
                "cover" -> if (turnOn) "open_cover" else "close_cover"
                else -> if (turnOn) "turn_on" else "turn_off"
            }
            CardAction.CallService(
                domain,
                service,
                JsonObject(mapOf("entity_id" to JsonArray(members.map { JsonPrimitive(it.entityId) }))),
                target = null,
            )
        }
}

/**
 * One entity row (`hui-generic-entity-row` with its domain's row element).
 *
 * @property state the formatted state, shown when the row has no control
 * @property control the row's control, `null` for a text-only row
 * @property actions the gestures of the row's name and icon (more-info by default)
 * @property badge the row's icon (or picture) as `state-badge` draws it, `null` for a missing entity
 * @property domain what a domain's own row adds (a second line, a weather picture), `null` for the others
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
    val badge: StateBadge? = null,
    val domain: DomainRow? = null,
)

/** The control on the right of an entity row. */
sealed interface RowControl {
    /**
     * A switch (`ha-entity-toggle`), [action] turns the entity on or off. [updatedAt] is when the entity's state
     * object last changed (epoch seconds), so the switch knows when the server answered.
     */
    data class Toggle(
        val checked: Boolean,
        val enabled: Boolean,
        val action: CardAction.CallService,
        val updatedAt: Double,
    ) : RowControl

    /** Text buttons, such as "Press", "Activate", "Run" or "Unlock". */
    data class Buttons(val buttons: List<RowButton>) : RowControl

    /**
     * Icon buttons: a cover's open, stop and close (`ha-cover-controls`), or the off and on of an entity whose
     * state is only assumed (`ha-entity-toggle`).
     */
    data class IconButtons(val buttons: List<RowIconButton>) : RowControl
}

/**
 * An icon button of a row: [shown] unless its feature isn't supported (it keeps its place), [active] when it is the
 * entity's current state.
 */
data class RowIconButton(
    val icon: String,
    val label: String,
    val enabled: Boolean,
    val shown: Boolean,
    val active: Boolean,
    val action: CardAction.CallService,
)

/** A text button of an entity row. */
data class RowButton(val label: String, val enabled: Boolean, val danger: Boolean, val action: CardAction.CallService)

/**
 * Derive an entities card. Each entry is an entity id or a row config: an entity's row, or a special row by its
 * `type` (dividers, sections, links, buttons...), hidden rows left out. Ports of `hui-entities-card`,
 * `DOMAIN_TO_ELEMENT_TYPE` and the entity and special rows (frontend@20260624.6
 * src/panels/lovelace/cards/hui-entities-card.ts, src/panels/lovelace/create-element/create-row-element.ts,
 * src/panels/lovelace/entity-rows/, src/panels/lovelace/special-rows/). [context] decides conditional rows.
 */
fun HassSnapshot.entitiesModel(
    card: CardConfig,
    now: Instant,
    context: ConditionContext = ConditionContext(),
): EntitiesModel {
    val configs = (card.json["entities"] as? JsonArray).orEmpty().mapNotNull { entry ->
        when {
            entry is JsonObject -> entry
            entry is JsonPrimitive && entry.isString -> JsonObject(mapOf("entity" to entry))
            else -> null
        }?.takeIf { it.string("type") != null || it.string("entity") != null }
    }
    return EntitiesModel(
        title = card.json.string("title")?.ifEmpty { null },
        icon = card.json.string("icon")?.ifEmpty { null },
        rows = configs.mapNotNull { entitiesRow(it, now, cardStateColor(card), context) },
        headerToggle = headerToggle(card.json, configs.mapNotNull { it.string("entity") }),
        header = headerFooterModel(card.json.obj("header")),
        footer = headerFooterModel(card.json.obj("footer")),
    )
}

/**
 * The header's switch: shown when asked (`show_header_toggle`), or by default with a title and at least two
 * entities that toggle (`shouldShowHeaderToggle`); `null` when hidden or without an entity to toggle.
 */
private fun HassSnapshot.headerToggle(card: JsonObject, entityIds: List<String>): HeaderToggle? {
    val shown = card.boolean("show_header_toggle")
        ?: (card.string("title") != null && entityIds.count { it.substringBefore('.') in DOMAINS_TOGGLE } >= 2)
    val entities = entityIds.filter { it.substringBefore('.') in DOMAINS_TOGGLE }.mapNotNull(states::get)
    return HeaderToggle(entities.any { it.state == "on" }, entities).takeIf { shown && entities.isNotEmpty() }
}

/** The card's `state_color`, which rows without their own take; `null` when the card doesn't set it. */
private fun cardStateColor(card: CardConfig): Boolean? =
    if ("state_color" in card.json) card.json.boolean("state_color") else null

/** The row of the entity [config], shown by its domain or as the [kind] of row its `type` asks for. */
internal fun HassSnapshot.entityRow(config: JsonObject, now: Instant, kind: EntityRowKind): EntityRowModel {
    val entityId = config.string("entity").orEmpty()
    // Rows default to more-info on tap, like the tile card
    val actions = cardActions(CardConfig(JsonObject(config + ("type" to JsonPrimitive("tile"))))).card
    val state = states[entityId]
        ?: return EntityRowModel(entityId, entityId, "mdi:alert-circle", null, false, true, null, actions)
    val name = entityNameDisplay(state, config["name"])
    val domain = if (kind == EntityRowKind.DOMAIN) domainRow(state, config, now) else null
    val control = if (kind == EntityRowKind.DOMAIN) {
        domain?.control ?: rowControl(state, config) ?: inputRowControl(state, name)
    } else {
        null
    }
    return EntityRowModel(
        entityId = entityId,
        name = name,
        icon = entityIcon(entityId, config.string("icon")),
        state = if (control == null) domain?.state ?: rowStateText(state, config, now, kind) else null,
        available = state.state != STATE_UNAVAILABLE,
        missing = false,
        control = control,
        actions = actions,
        badge = stateBadge(state, config.string("icon"), config.boolean("state_color")),
        domain = domain,
    )
}

/**
 * The text of a row without a control: sensor rows show timestamps as relative times (`hui-sensor-entity-row`),
 * every other row its formatted state (`hui-simple-entity-row`).
 */
private fun HassSnapshot.rowStateText(state: EntityState, config: JsonObject, now: Instant, kind: EntityRowKind) =
    if (kind == EntityRowKind.SENSOR || (kind == EntityRowKind.DOMAIN && state.domain == "sensor")) {
        stateDisplay(state, JsonPrimitive("state"), now, StateDisplayOptions(timeFormat = config.string("time_format")))
    } else {
        formatEntityState(state)
    }

/** The control of [state]'s entities row (a switch or buttons), `null` for rows that show text. */
internal fun HassSnapshot.rowControl(state: EntityState, config: JsonObject): RowControl? {
    val available = state.state != STATE_UNAVAILABLE
    fun call(domain: String, service: String) =
        CardAction.CallService(domain, service, buildJsonObject { put("entity_id", state.entityId) }, target = null)
    fun button(label: String, action: CardAction.CallService, enabled: Boolean = available, danger: Boolean = false) =
        RowControl.Buttons(listOf(RowButton(label, enabled, danger, action)))
    val actionName = config.string("action_name")?.ifEmpty { null }
    return when (DOMAIN_ROWS[state.domain]) {
        ROW_TOGGLE -> if (state.state in TOGGLE_STATES) {
            RowControl.Toggle(state.state == "on", available, toggleEntity(state.entityId), state.lastUpdated)
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
internal fun HassSnapshot.scriptButtons(
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
private val STATES_OFF_TOGGLE = setOf("closed", "locked", "off")

private val TOGGLE_STATES = setOf("on", "off", STATE_UNAVAILABLE, STATE_UNKNOWN)

/** The ported part of `DOMAIN_TO_ELEMENT_TYPE`; other domains render as text rows. */
private val DOMAIN_ROWS = mapOf(
    "alert" to ROW_TOGGLE, "automation" to ROW_TOGGLE, "fan" to ROW_TOGGLE, "input_boolean" to ROW_TOGGLE,
    "light" to ROW_TOGGLE, "remote" to ROW_TOGGLE, "siren" to ROW_TOGGLE, "switch" to ROW_TOGGLE,
    "vacuum" to ROW_TOGGLE, "button" to ROW_BUTTON, "input_button" to ROW_INPUT_BUTTON, "scene" to ROW_SCENE,
    "script" to ROW_SCRIPT, "lock" to ROW_LOCK,
)
