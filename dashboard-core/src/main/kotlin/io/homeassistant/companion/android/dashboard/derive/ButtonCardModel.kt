package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.DOMAINS_TOGGLE
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.HVAC_ACTION_TO_MODE
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A button card: its icon (in [color]), name and state, each shown as configured, and what its gestures do.
 * Port of `hui-button-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-button-card.ts).
 *
 * @property missing why it can't show, when the entity it names doesn't exist (a warning shows instead)
 */
data class ButtonCardModel(
    val name: String?,
    val icon: String?,
    val state: String?,
    val color: DisplayColor?,
    val iconHeight: String?,
    val actions: ElementActions,
    val missing: String?,
    val brightness: Double? = null,
)

/** The button card of [card]. */
fun HassSnapshot.buttonCardModel(card: CardConfig): ButtonCardModel {
    val json = card.json
    val entityId = json.string("entity")
    val state = entityId?.let(states::get)
    // `getEntityDefaultButtonAction`: toggleable domains toggle, others open their details
    val tap = if (entityId != null && entityId.substringBefore('.') in DOMAINS_TOGGLE) "toggle" else "more-info"
    val defaults = buildJsonObject {
        put("tap_action", buildJsonObject { put("action", tap) })
        put("hold_action", buildJsonObject { put("action", "more-info") })
        put("double_tap_action", buildJsonObject { put("action", "none") })
    }
    val showName = json.boolean("show_name") != false
    val color = json.string("color") ?: "none".takeIf { json.boolean("state_color") == false }
    return ButtonCardModel(
        name = (
            if (state !=
                null
            ) {
                entityNameDisplay(state, json["name"])
            } else {
                json.string("name").orEmpty()
            }
            ).takeIf { showName },
        icon = entityIcon(entityId.orEmpty(), configIcon = json.string("icon")).takeIf {
            json.boolean("show_icon") !=
                false
        },
        state = state?.takeIf { json.boolean("show_state") == true }?.let { formatEntityState(it) },
        // The button's inactive colour is its icon colour (`--state-inactive-color: var(--state-icon-color)`)
        color = if (color == "none") null else buttonColor(state, color)?.withInactiveAsIcon(),
        brightness = state?.let(::iconBrightness),
        iconHeight = json.string("icon_height"),
        actions = elementActions(JsonObject(defaults + json)),
        missing = localize("ui.card.common.entity_not_found").takeIf { entityId != null && state == null },
    )
}

/** Port of `_computeColor`: a configured colour while active, else the light's colour, the climate's action's, or the state's. */
private fun buttonColor(state: EntityState?, color: String?): DisplayColor? = when {
    color != null -> cssColor(color).takeIf { state == null || state.isActive() }
    state == null -> null
    state.attributes["rgb_color"] is JsonArray -> (state.attributes["rgb_color"] as JsonArray)
        .mapNotNull { (it as? JsonPrimitive)?.content }
        .let { DisplayColor.Literal("rgb(${it.joinToString(",")})") }
    state.attributes.string("hvac_action") != null ->
        HVAC_ACTION_TO_MODE[state.attributes.string("hvac_action")]?.let { stateColor(state, it) }
    else -> stateColor(state)
}
