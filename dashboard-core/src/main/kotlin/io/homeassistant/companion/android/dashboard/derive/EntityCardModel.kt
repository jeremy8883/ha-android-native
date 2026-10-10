package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.ValueParts
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValueParts
import io.homeassistant.companion.android.dashboard.display.formatEntityStateParts
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.history.GraphHistoryKey
import io.homeassistant.companion.android.dashboard.history.SensorGraph
import io.homeassistant.companion.android.dashboard.history.historyKey
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

/**
 * An entity card (or a sensor card, an entity card with a graph): what it shows, or why it can't.
 * Ports of `hui-entity-card` and `hui-sensor-card` (frontend@20260624.6 src/panels/lovelace/cards/).
 */
sealed interface EntityCardModel {
    /** A warning in its place: the entity doesn't exist, or the card's config is invalid. */
    data class Warning(val text: String) : EntityCardModel

    /**
     * The entity's [name] and [icon] (in [color] when coloured) above its [value], and a [graph] under it for a
     * sensor card with one.
     */
    data class Shown(
        val name: String,
        val icon: String?,
        val color: DisplayColor?,
        val iconHeight: String?,
        val brightness: Double?,
        val value: ValueParts,
        val actions: ElementActions,
        val graph: SensorGraph?,
    ) : EntityCardModel
}

/** The entity or sensor card of [card]. */
fun HassSnapshot.entityCardModel(card: CardConfig): EntityCardModel {
    val json = card.json
    val entityId = json.string("entity")
    // A config the card rejects shows an error card, its message only in the editor's preview
    val invalid = entityId.isNullOrEmpty() || (card.type == SENSOR && entityId.substringBefore('.') !in SENSOR_DOMAINS)
    val state = entityId?.let(states::get)
    val colored = json.boolean("state_color") ?: (state?.domain == "light")
    return when {
        entityId == null || invalid -> EntityCardModel.Warning(localize("ui.errors.config.configuration_error"))
        state == null -> EntityCardModel.Warning(localize("ui.card.common.entity_not_found"))
        else -> EntityCardModel.Shown(
            name = entityNameDisplay(state, json["name"]),
            icon = entityIcon(entityId, configIcon = json.string("icon")),
            // Lights are coloured unless told otherwise, other entities only when asked
            // The icon's inactive colour is its own (`--state-inactive-color: var(--state-icon-color)`)
            color = if (colored) entityCardColor(state)?.withInactiveAsIcon() else null,
            iconHeight = json.string("icon_height"),
            brightness = iconBrightness(state).takeIf { colored },
            value = entityCardValue(state, json),
            actions = elementActions(json, tapWhenUnset = true),
            graph = sensorGraph(card),
        )
    }
}

/** Port of the entity card's `_computeColor`: a climate's action's, a light's own, else the state's. */
private fun entityCardColor(state: EntityState): DisplayColor? = badgeColor(state)

/**
 * The value (an attribute's when configured, without its unit) and unit shown: a configured unit trails, and
 * unknown, unavailable and duration states have none. Port of the card's render and `computeEntityUnitDisplay`.
 */
private fun HassSnapshot.entityCardValue(state: EntityState, json: JsonObject): ValueParts {
    val attribute = json.string("attribute")?.takeIf { "attribute" in json }
    val customUnit = json.string("unit")?.ifEmpty { null }
    val parts = if (attribute != null) {
        formatEntityAttributeValueParts(state, attribute)
            .takeIf { attribute in state.attributes }
            ?: ValueParts(localize("state.default.unknown"))
    } else {
        formatEntityStateParts(state)
    }
    val noUnit = state.state == STATE_UNAVAILABLE ||
        state.state == STATE_UNKNOWN ||
        (attribute == null && state.attributes.string("device_class") == "duration")
    val unit = when {
        noUnit -> null
        customUnit != null -> customUnit
        // The unit of the attribute's value, even when it isn't one
        attribute != null -> formatEntityAttributeValueParts(state, attribute).unit
        else -> parts.unit
    }
    return ValueParts(
        value = parts.value,
        unit = unit,
        // The unit follows the locale's order, a configured one always trails; attributes' always trail
        unitFirst = attribute == null && customUnit == null && parts.unitFirst,
    )
}

/**
 * The graph a sensor card adds under its value (`graph: line`): the last day by default, one point per hour; `null`
 * for other cards and invalid sensor cards.
 */
private fun sensorGraph(card: CardConfig): SensorGraph? {
    val json = card.json
    val entityId = json.string("entity")?.takeIf { it.substringBefore('.') in SENSOR_DOMAINS }
    if (card.type != SENSOR || entityId == null || json.string("graph") != "line") return null
    val limits = json.obj("limits")
    return SensorGraph(
        entityId = entityId,
        hoursToShow = number(json["hours_to_show"])?.takeIf { it > 0 } ?: DEFAULT_HOURS_TO_SHOW,
        detail = number(json["detail"])?.toInt()?.takeIf { it == 2 } ?: 1,
        minY = number(limits?.get("min")),
        maxY = number(limits?.get("max")),
    )
}

private const val SENSOR = "sensor"
private const val DEFAULT_HOURS_TO_SHOW = 24.0
private val SENSOR_DOMAINS = setOf("counter", "input_number", "number", "sensor")

/**
 * The histories the graphs of [cards] draw, when the server records history (`isComponentLoaded(history)`, as
 * the graph checks before it subscribes).
 */
fun HassSnapshot.graphHistoryRequests(cards: List<CardConfig>): Set<GraphHistoryKey> {
    if (HISTORY_COMPONENT !in config.components) return emptySet()
    return cards.mapNotNull { sensorGraph(it)?.historyKey }.toSet()
}

private const val HISTORY_COMPONENT = "history"
