package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.history.SensorGraph
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The header and footer elements of the entities card (frontend@20260624.6 src/panels/lovelace/header-footer/):
// `hui-picture-header-footer`, `hui-buttons-header-footer` (with `hui-buttons-base`) and
// `hui-graph-header-footer`.

/** An entities card's `header` or `footer` element. */
sealed interface HeaderFooterModel {
    /** A full-width picture ([image], a server path or a URL), acting on its own actions only. */
    data class Picture(val image: String, val altText: String?, val actions: ElementActions) : HeaderFooterModel

    /** A row of chips, one per entity. */
    data class Buttons(val buttons: List<HeaderFooterButton>) : HeaderFooterModel

    /** A line graph of an entity's recent states, opening its details on tap. */
    data class Graph(val graph: SensorGraph) : HeaderFooterModel

    /**
     * The element's config is invalid: `hui-error-card` in its place, titled [message] (upstream's own reason only
     * shows in the editor's preview).
     */
    data class Invalid(val message: String) : HeaderFooterModel
}

/**
 * A chip of a buttons header or footer: its [name] (empty when hidden) and [badge] (`null` when `show_icon: false`
 * or the entity is missing without an icon), toggling on tap and opening the details on hold by default.
 */
data class HeaderFooterButton(
    val entityId: String,
    val name: String,
    val badge: StateBadge?,
    val actions: ElementActions,
)

/** The element of an entities card's `header` or `footer` [config], `null` without one. */
fun HassSnapshot.headerFooterModel(config: JsonObject?): HeaderFooterModel? {
    config ?: return null
    val invalid = HeaderFooterModel.Invalid(localize("ui.errors.config.configuration_error"))
    return when (config.string("type")) {
        PICTURE -> config.string("image")?.ifEmpty { null }
            ?.let { HeaderFooterModel.Picture(it, config.string("alt_text"), elementActions(config)) }
            ?: invalid
        BUTTONS -> buttonsModel(config) ?: invalid
        GRAPH -> headerFooterGraph(config)?.let(HeaderFooterModel::Graph) ?: invalid
        else -> invalid
    }
}

/** The graphs the header and footer of an entities [card] draw, for their histories to be loaded. */
fun headerFooterGraphs(card: CardConfig): List<SensorGraph> = if (card.type != ENTITIES) {
    emptyList()
} else {
    listOfNotNull(card.json.obj(HEADER), card.json.obj(FOOTER))
        .filter { it.string("type") == GRAPH }
        .mapNotNull(::headerFooterGraph)
}

/** Port of `HuiGraphHeaderFooter.setConfig`: the last day by default, detail 1 unless 2. */
private fun headerFooterGraph(config: JsonObject): SensorGraph? {
    val entityId = config.string("entity")?.takeIf { it.substringBefore('.') in GRAPH_DOMAINS } ?: return null
    val limits = config.obj("limits")
    return SensorGraph(
        entityId = entityId,
        hoursToShow = number(config["hours_to_show"]) ?: DEFAULT_HOURS_TO_SHOW,
        detail = number(config["detail"])?.toInt()?.takeIf { it == 2 } ?: 1,
        minY = number(limits?.get("min")),
        maxY = number(limits?.get("max")),
    )
}

/** Ports of `HuiButtonsHeaderFooter.setConfig` and `HuiButtonsBase.render`; `null` when the config is invalid. */
private fun HassSnapshot.buttonsModel(config: JsonObject): HeaderFooterModel? {
    val entries = config["entities"] as? JsonArray
    // An entry that is neither an entity id nor an object with one makes the whole element invalid
    val configs = entries?.mapNotNull { entry ->
        when {
            entry is JsonPrimitive && entry.isString -> JsonObject(mapOf(ENTITY to entry))
            entry is JsonObject && ENTITY in entry -> entry
            else -> null
        }
    }?.takeIf { it.size == entries.size }
    return configs?.let { HeaderFooterModel.Buttons(it.map { entry -> buttonModel(withDefaultActions(entry)) }) }
}

/** Toggle on tap and details on hold unless configured; a scene is turned on whatever its `tap_action`. */
private fun withDefaultActions(config: JsonObject): JsonObject {
    val entityId = config.string(ENTITY).orEmpty()
    val defaults = buildJsonObject {
        put("tap_action", buildJsonObject { put("action", "toggle") })
        put("hold_action", buildJsonObject { put("action", "more-info") })
    }
    val merged = JsonObject(defaults + config)
    if (entityId.substringBefore('.') != SCENE) return merged
    val sceneOn = buildJsonObject {
        put("action", "call-service")
        put("service", "scene.turn_on")
        put("target", buildJsonObject { put("entity_id", entityId) })
    }
    return JsonObject(merged + ("tap_action" to sceneOn))
}

private fun HassSnapshot.buttonModel(config: JsonObject): HeaderFooterButton {
    val entityId = config.string(ENTITY).orEmpty()
    val state = states[entityId]
    return HeaderFooterButton(entityId, buttonName(config, state), buttonBadge(config, state), elementActions(config))
}

/** `hui-buttons-base`'s name: shown with `show_name: true` (for an existing entity) or a configured name. */
private fun buttonName(config: JsonObject, state: EntityState?): String {
    val configName = config.string("name")
    val showName = config.boolean("show_name")
    val shown = (showName == true && state != null) || (configName != null && showName != false)
    return if (shown) configName?.ifEmpty { null } ?: state?.stateName().orEmpty() else ""
}

/** The chip's `state-badge` (coloured by state), `null` with `show_icon: false` or nothing to draw. */
private fun HassSnapshot.buttonBadge(config: JsonObject, state: EntityState?): StateBadge? {
    val icon = config.string("icon")?.ifEmpty { null }
    val image = config.string("image")?.ifEmpty { null }
    return when {
        config.boolean("show_icon") == false -> null
        state != null -> stateBadge(state, icon, stateColor = true).let { badge ->
            image?.let { badge.copy(picture = it) } ?: badge
        }
        icon != null || image != null -> StateBadge(icon, image, null, null, unavailable = false)
        else -> null
    }
}

private const val ENTITIES = "entities"
private const val ENTITY = "entity"
private const val HEADER = "header"
private const val FOOTER = "footer"
private const val PICTURE = "picture"
private const val BUTTONS = "buttons"
private const val GRAPH = "graph"
private const val SCENE = "scene"
private const val DEFAULT_HOURS_TO_SHOW = 24.0
private val GRAPH_DOMAINS = setOf("counter", "input_number", "number", "sensor")
