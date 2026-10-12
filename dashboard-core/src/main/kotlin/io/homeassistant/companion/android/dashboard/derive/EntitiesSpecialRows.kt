package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.DOMAINS_TOGGLE
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.conditionsMet
import io.homeassistant.companion.android.dashboard.display.attributeTimestampDisplay
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValueParts
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The rows of an entities card that aren't an entity's own (frontend@20260624.6 src/panels/lovelace/special-rows/),
// and the choice between them by `type` (`createRowElement`, src/panels/lovelace/create-element/create-row-element.ts).

/** A row of an entities card: an entity's row, or one of the special rows chosen by its `type`. */
sealed interface EntitiesRow {
    /** An entity's row (`hui-*-entity-row`), also the `attribute` row, which shows an attribute in its place. */
    data class Entity(val row: EntityRowModel) : EntitiesRow

    /** `hui-divider-row`: a line across the row. */
    data object Divider : EntitiesRow

    /** `hui-section-row`: a line across the card, then the [label] when it has one. */
    data class Section(val label: String?) : EntitiesRow

    /** `hui-weblink-row`: a link named [name] with its [icon], doing [action] on tap. */
    data class Weblink(val name: String, val icon: String, val action: CardAction) : EntitiesRow

    /**
     * `hui-button-row` (and `hui-call-service-row`): the [icon] (none without an icon or entity) and [name], then a
     * button labelled [label] running [actions].
     */
    data class Button(val name: String, val icon: String?, val label: String, val actions: ElementActions) :
        EntitiesRow

    /** `hui-buttons-row`: a chip per entity, as the buttons header and footer draw them. */
    data class Buttons(val buttons: List<HeaderFooterButton>) : EntitiesRow

    /**
     * `hui-cast-row`: the [icon] and [name], then why it can't cast ([status]): this app has no cast sender, as the
     * frontend has no cast API in the companion app's web view.
     */
    data class Cast(val name: String, val icon: String, val status: String) : EntitiesRow

    /** `hui-text-row`: the [icon] (an empty space without one), the [name], then the [text] at the end. */
    data class Text(val name: String, val text: String, val icon: String?) : EntitiesRow

    /** A row whose config is invalid: `hui-error-card` in its place, titled [message]. */
    data class Invalid(val message: String) : EntitiesRow
}

/**
 * The row of the entities card entry [config], `null` when it is hidden (a conditional row whose conditions aren't
 * met, a cast row hidden while it can't cast). Port of `HuiEntitiesCard._renderEntity` and `createRowElement`: rows
 * without a `type` (and conditional ones) take the card's `state_color` when it sets one, `perform-action` rows are
 * `call-service` rows.
 */
internal fun HassSnapshot.entitiesRow(
    config: JsonObject,
    now: Instant,
    cardStateColor: Boolean?,
    context: ConditionContext,
): EntitiesRow? {
    val type = config.string(TYPE)
    val withStateColor = if (cardStateColor != null && (type == null || type == CONDITIONAL)) {
        JsonObject(mapOf(STATE_COLOR to JsonPrimitive(cardStateColor)) + config)
    } else {
        config
    }
    return rowElement(withStateColor, now, context)
}

/** Port of `createRowElement` for an entry with its `state_color` already resolved. */
private fun HassSnapshot.rowElement(config: JsonObject, now: Instant, context: ConditionContext): EntitiesRow? =
    when (val type = config.string(TYPE)) {
        null -> config.string(ENTITY)?.let { EntitiesRow.Entity(entityRow(config, now, EntityRowKind.DOMAIN)) }
            ?: invalidRow()
        DIVIDER -> EntitiesRow.Divider
        SECTION -> EntitiesRow.Section(config.string("label")?.ifEmpty { null })
        WEBLINK -> weblinkRow(config)
        BUTTON -> buttonRow(config)
        CALL_SERVICE, PERFORM_ACTION -> callServiceRow(config)
        BUTTONS -> buttonsRow(config)
        CAST -> castRow(config)
        CONDITIONAL -> conditionalRow(config, now, context)
        ATTRIBUTE -> attributeRow(config, now)
        TEXT -> textRow(config)
        in ENTITY_ROW_TYPES -> config.string(ENTITY)?.let {
            EntitiesRow.Entity(entityRow(config, now, ENTITY_ROW_TYPES.getValue(type)))
        } ?: invalidRow()
        else -> invalidRow()
    }

/** `hui-error-card` outside the editor: only its title, "Configuration error". */
private fun HassSnapshot.invalidRow() = EntitiesRow.Invalid(localize("ui.errors.config.configuration_error"))

/**
 * Port of `HuiWeblinkRow`: named after its URL and with a link icon by default; a URL with a scheme (or one set to
 * open in a new tab or download) opens in the browser, a path opens in the app.
 */
private fun HassSnapshot.weblinkRow(config: JsonObject): EntitiesRow {
    val url = config.string("url")?.ifEmpty { null } ?: return invalidRow()
    val external = "://" in url || config.boolean("new_tab") == true || config.boolean("download") == true
    return EntitiesRow.Weblink(
        name = config.string("name") ?: url,
        icon = config.string("icon") ?: WEBLINK_ICON,
        action = if (external) CardAction.OpenUrl(url) else CardAction.Navigate(url, replace = false),
    )
}

/**
 * Port of `HuiButtonRow`: toggles a toggleable entity on tap and opens the details otherwise or on hold, unless
 * configured; "Run" unless `action_name` names the button.
 */
private fun HassSnapshot.buttonRow(config: JsonObject): EntitiesRow {
    val entityId = config.string(ENTITY)?.ifEmpty { null }
    val name = config.string("name")?.ifEmpty { null }
    if (name == null && entityId == null) return invalidRow()
    val defaults = buildJsonObject {
        put(TAP_ACTION, actionObject(if (entityId?.substringBefore('.') in DOMAINS_TOGGLE) "toggle" else MORE_INFO))
        put(HOLD_ACTION, actionObject(MORE_INFO))
    }
    val state = entityId?.let(states::get)
    return EntitiesRow.Button(
        name = state?.let { entityNameDisplay(it, config["name"]) } ?: name.orEmpty(),
        icon = config.string(ICON)?.ifEmpty { null } ?: state?.let { entityIcon(it.entityId) },
        label = config.string("action_name")?.ifEmpty { null } ?: localize("ui.card.service.run"),
        actions = elementActions(JsonObject(defaults + config)),
    )
}

/** Port of `HuiCallServiceRow`: a button row performing its `action` (or `service`) with its `data` on tap. */
private fun HassSnapshot.callServiceRow(config: JsonObject): EntitiesRow {
    val action = config.string("action")?.ifEmpty { null } ?: config.string("service")?.ifEmpty { null }
    if (config.string("name").isNullOrEmpty() || action == null) return invalidRow()
    val tap = buildJsonObject {
        put("action", "perform-action")
        put("perform_action", action)
        (config["data"] ?: config["service_data"])?.let { put("data", it) }
    }
    return buttonRow(JsonObject(mapOf(TAP_ACTION to tap) + config))
}

/**
 * Port of `HuiButtonsRow`: `hui-buttons-base` with each entity toggling on tap when it can (else opening its
 * details) and opening its details on hold, unless configured.
 */
private fun HassSnapshot.buttonsRow(config: JsonObject): EntitiesRow {
    val entries = config["entities"] as? JsonArray ?: return invalidRow()
    val configs = entries.map { entry ->
        when {
            entry is JsonPrimitive && entry.isString -> JsonObject(mapOf(ENTITY to entry))
            entry is JsonObject && (TYPE in entry || ENTITY in entry) -> entry
            else -> return invalidRow()
        }
    }
    return EntitiesRow.Buttons(
        configs.map { entry ->
            val entityId = entry.string(ENTITY)
            val defaults = buildJsonObject {
                put(
                    TAP_ACTION,
                    actionObject(
                        if (entityId?.substringBefore('.') in
                            DOMAINS_TOGGLE
                        ) {
                            "toggle"
                        } else {
                            MORE_INFO
                        },
                    ),
                )
                put(HOLD_ACTION, actionObject(MORE_INFO))
            }
            buttonModel(JsonObject(defaults + entry))
        },
    )
}

/**
 * Port of `HuiCastRow` where the cast API is unavailable (`getCastManager` fails): its name and icon, then "Cast API
 * unavailable"; hidden with `hide_if_unavailable`.
 */
private fun castRow(config: JsonObject): EntitiesRow? = if (config.boolean("hide_if_unavailable") == true) {
    null
} else {
    EntitiesRow.Cast(
        name = config.string("name") ?: CAST_NAME,
        icon = config.string(ICON) ?: CAST_ICON,
        status = CAST_UNAVAILABLE,
    )
}

/**
 * Port of `HuiConditionalRow`: its `row` while its `conditions` are met (taking the card's `state_color` along),
 * hidden otherwise; invalid without conditions or a row.
 */
private fun HassSnapshot.conditionalRow(config: JsonObject, now: Instant, context: ConditionContext): EntitiesRow? {
    val conditions = config["conditions"] as? JsonArray ?: return invalidRow()
    val row = config.obj("row") ?: return invalidRow()
    if (!conditionsMet(conditions.filterIsInstance<JsonObject>(), context)) return null
    val inner = if (config.boolean(STATE_COLOR) ==
        true
    ) {
        JsonObject(mapOf(STATE_COLOR to JsonPrimitive(true)) + row)
    } else {
        row
    }
    return rowElement(inner, now, context)
}

/**
 * Port of `HuiAttributeRow`: an entity's row showing one of its attributes, between the `prefix` and `suffix`; a
 * date in its `time_format` when it has one, "—" when the attribute is missing.
 */
private fun HassSnapshot.attributeRow(config: JsonObject, now: Instant): EntitiesRow {
    val entityId = config.string(ENTITY)?.ifEmpty { null }
    val attribute = config.string("attribute")?.ifEmpty { null }
    if (entityId == null || attribute == null) return invalidRow()
    val row = entityRow(config, now, SIMPLE)
    val state = states[entityId] ?: return EntitiesRow.Entity(row)
    val value = state.attributes[attribute]
    val timeFormat = config.string("time_format")?.ifEmpty { null }
    val suffix = config.string("suffix")
    val text = timeFormat?.let { attributeTimestampDisplay(value, now, it) }
        ?: when {
            value == null -> NO_VALUE
            // `ha-attribute-value` hides the unit when the row has its own suffix
            !suffix.isNullOrEmpty() && value !is JsonNull -> formatEntityAttributeValueParts(state, attribute).value
            else -> formatEntityAttributeValue(state, attribute)
        }
    return EntitiesRow.Entity(row.copy(state = config.string("prefix").orEmpty() + text + suffix.orEmpty()))
}

/** Port of `HuiTextRow`: invalid without a name and a text. */
private fun HassSnapshot.textRow(config: JsonObject): EntitiesRow {
    val name = config.string("name")?.ifEmpty { null }
    val text = config.string("text")?.ifEmpty { null }
    if (name == null || text == null) return invalidRow()
    return EntitiesRow.Text(name, text, config.string(ICON)?.ifEmpty { null })
}

private fun actionObject(action: String) = buildJsonObject { put("action", action) }

/** How a typed entity row shows its entity. */
internal enum class EntityRowKind {
    /** By its domain, as a row without a `type`. */
    DOMAIN,

    /** `hui-simple-entity-row`: the formatted state as text. */
    SIMPLE,

    /** `hui-sensor-entity-row`: the state as text, timestamps as relative times. */
    SENSOR,
}

private val SIMPLE = EntityRowKind.SIMPLE

/** The entity row `type`s (`<type>-entity`), and how each shows its entity. */
private val ENTITY_ROW_TYPES = mapOf("simple-entity" to EntityRowKind.SIMPLE, "sensor-entity" to EntityRowKind.SENSOR) +
    listOf(
        "button", "climate", "cover", "date", "datetime", "event", "group", "humidifier", "input-button",
        "input-datetime", "input-number", "input-select", "input-text", "lock", "media-player", "number", "scene",
        "script", "select", "text", "time", "timer", "toggle", "update", "valve", "weather",
    ).associate { "$it-entity" to EntityRowKind.DOMAIN }

private const val TYPE = "type"
private const val ENTITY = "entity"
private const val ICON = "icon"
private const val STATE_COLOR = "state_color"
private const val TAP_ACTION = "tap_action"
private const val HOLD_ACTION = "hold_action"
private const val MORE_INFO = "more-info"
private const val DIVIDER = "divider"
private const val SECTION = "section"
private const val WEBLINK = "weblink"
private const val BUTTON = "button"
private const val BUTTONS = "buttons"
private const val CALL_SERVICE = "call-service"
private const val PERFORM_ACTION = "perform-action"
private const val CAST = "cast"
private const val CONDITIONAL = "conditional"
private const val ATTRIBUTE = "attribute"
private const val TEXT = "text"
private const val WEBLINK_ICON = "mdi:link"
private const val CAST_ICON = "mdi:television"
private const val NO_VALUE = "—"

// `HuiCastRow`'s defaults and text, in English upstream too
private const val CAST_NAME = "Home Assistant Cast"
private const val CAST_UNAVAILABLE = "Cast API unavailable"
