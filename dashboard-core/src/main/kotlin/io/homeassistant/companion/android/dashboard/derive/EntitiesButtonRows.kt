package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.DOMAINS_TOGGLE
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The button rows of an entities card (frontend@20260624.6 src/panels/lovelace/special-rows/hui-button-row.ts,
// hui-call-service-row.ts, hui-buttons-row.ts).

/**
 * Port of `HuiButtonRow`: toggles a toggleable entity on tap and opens the details otherwise or on hold, unless
 * configured; "Run" unless `action_name` names the button.
 */
internal fun HassSnapshot.buttonRow(config: JsonObject): EntitiesRow {
    val entityId = config.string(ENTITY)?.ifEmpty { null }
    val name = config.string("name")?.ifEmpty { null }
    if (name == null && entityId == null) return invalidRow()
    val state = entityId?.let(states::get)
    return EntitiesRow.Button(
        name = state?.let { entityNameDisplay(it, config["name"]) } ?: name.orEmpty(),
        icon = config.string(ICON)?.ifEmpty { null } ?: state?.let { entityIcon(it.entityId) },
        label = config.string("action_name")?.ifEmpty { null } ?: localize("ui.card.service.run"),
        actions = elementActions(JsonObject(buttonDefaults(entityId) + config)),
    )
}

/** Port of `HuiCallServiceRow`: a button row performing its `action` (or `service`) with its `data` on tap. */
internal fun HassSnapshot.callServiceRow(config: JsonObject): EntitiesRow {
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
 * details) and opening its details on hold, unless configured; invalid when an entry is neither an entity id nor a
 * config with an `entity` or a `type`.
 */
internal fun HassSnapshot.buttonsRow(config: JsonObject): EntitiesRow {
    val configs = (config["entities"] as? JsonArray)?.map(::buttonsEntryConfig)?.takeIf { null !in it }
    return configs?.filterNotNull()?.let { entries ->
        EntitiesRow.Buttons(entries.map { buttonModel(JsonObject(buttonDefaults(it.string(ENTITY)) + it)) })
    } ?: invalidRow()
}

/** An entry of a buttons row as a config, `null` when it is invalid (`processConfigEntities`). */
private fun buttonsEntryConfig(entry: JsonElement): JsonObject? = when {
    entry is JsonPrimitive && entry.isString -> JsonObject(mapOf(ENTITY to entry))
    entry is JsonObject && (TYPE in entry || ENTITY in entry) -> entry
    else -> null
}

/** The default actions of a button for [entityId]: toggle on tap when it can, else its details; details on hold. */
private fun buttonDefaults(entityId: String?): JsonObject = buildJsonObject {
    put(TAP_ACTION, actionObject(if (entityId?.substringBefore('.') in DOMAINS_TOGGLE) "toggle" else MORE_INFO))
    put(HOLD_ACTION, actionObject(MORE_INFO))
}

private fun actionObject(action: String) = buildJsonObject { put("action", action) }

private const val TYPE = "type"
private const val ENTITY = "entity"
private const val ICON = "icon"
private const val TAP_ACTION = "tap_action"
private const val HOLD_ACTION = "hold_action"
private const val MORE_INFO = "more-info"
