package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The area controls feature of the area card: `hui-area-controls-card-feature` (frontend@20260624.6
// src/panels/lovelace/card-features/hui-area-controls-card-feature.ts), with `getAreaControlEntities`
// (src/data/area/area_controls.ts) and `computeGroupEntitiesState`, `toggleGroupEntities`
// (src/common/entity/group_entities.ts). The area card's other features need an entity, so they never show.

/** An `area-controls` feature: a row of [buttons], each toggling a kind of device of the area (or one entity). */
data class AreaControls(val buttons: List<AreaControlButton>)

/**
 * One area control: its [icon], [active] (filled with [activeColor]) while what it controls is on or open, and
 * the [action] toggling it all, labelled [label].
 */
data class AreaControlButton(
    val label: String,
    val icon: String,
    val active: Boolean,
    val activeColor: DisplayColor,
    val action: CardAction.CallService,
)

/** The `area-controls` features of the card [json] for [areaId], those with nothing to control left out. */
internal fun HassSnapshot.areaControlFeatures(
    json: JsonObject,
    areaId: String,
    excluded: Set<String>,
): List<AreaControls> = json.objects("features").filter { it.string("type") == AREA_CONTROLS }.mapNotNull { feature ->
    areaControls(feature, areaId, excluded).takeIf { it.buttons.isNotEmpty() }
}

private fun HassSnapshot.areaControls(feature: JsonObject, areaId: String, excluded: Set<String>): AreaControls {
    val configured = feature["controls"] as? JsonArray
    val controls = configured?.mapNotNull(::controlOf) ?: AREA_CONTROL_DOMAINS.map(Control::Domain)
    val buttons = controls.mapNotNull { control ->
        when (control) {
            is Control.Domain -> domainButton(control.domain, areaId, excluded)
            is Control.Entity -> states[control.entityId]?.takeIf { it.entityId !in excluded }?.let(::entityButton)
        }
    }
    // Without configured controls, the first few that apply
    return AreaControls(if (configured == null) buttons.take(MAX_DEFAULT_AREA_CONTROLS) else buttons)
}

/** A configured control, normalized: an area control domain, or an entity. */
private sealed interface Control {
    data class Domain(val domain: String) : Control

    data class Entity(val entityId: String) : Control
}

private fun controlOf(element: JsonElement): Control? = when {
    element is JsonObject -> element.string("entity_id")?.let(Control::Entity)
    element.stringOrNull in AREA_CONTROL_DOMAINS -> Control.Domain(element.stringOrNull.orEmpty())
    else -> element.stringOrNull?.let(Control::Entity)
}

/** A button for all of the area's entities of a control domain (`light`, `cover-blind`, ...), `null` without any. */
private fun HassSnapshot.domainButton(control: String, areaId: String, excluded: Set<String>): AreaControlButton? {
    val domain = control.substringBefore('-')
    val deviceClass = control.substringAfter('-', "").ifEmpty { null }
    val entities = areaEntities(areaId, setOf(domain), deviceClass?.let(::setOf), excluded).mapNotNull(states::get)
    val first = entities.firstOrNull() ?: return null
    val groupState = groupEntitiesState(entities)
    val active = first.isActive(groupState)
    return AreaControlButton(
        label = localize("ui.card_features.area_controls.$control.${if (active) "off" else "on"}"),
        icon = domainIcon(domain, deviceClass, groupState),
        active = active,
        activeColor = domainActiveColor(domain, deviceClass, groupState),
        action = toggleGroup(entities),
    )
}

private fun HassSnapshot.entityButton(state: EntityState): AreaControlButton {
    val active = state.isActive()
    return AreaControlButton(
        label = localize("ui.card.common.turn_${if (active) "off" else "on"}"),
        icon = entityIcon(state.entityId) ?: fallbackDomainIcon(state.domain),
        active = active,
        activeColor = domainActiveColor(state.domain, null, state.state),
        action = toggleGroup(listOf(state)),
    )
}

/** Port of `computeGroupEntitiesState`: covers by their most moving state, others on when any is. */
internal fun groupEntitiesState(states: List<EntityState>): String = when {
    states.isEmpty() || states.all { it.state == STATE_UNAVAILABLE } -> STATE_UNAVAILABLE
    states.none { it.state != STATE_UNAVAILABLE && it.state != STATE_UNKNOWN } -> STATE_UNKNOWN
    states.first().domain == COVER ->
        COVER_GROUP_STATES.firstOrNull { value -> states.any { it.state == value } } ?: "closed"
    states.any { it.state == "on" } -> "on"
    else -> "off"
}

/** Port of `toggleGroupEntities`: off (or closed) when on (or open), a moving cover stopped, else on. */
private fun toggleGroup(states: List<EntityState>): CardAction.CallService {
    val domain = states.first().domain
    val state = groupEntitiesState(states)
    val on = state == "on" || state == "open"
    val service = when {
        domain != COVER -> if (on) "turn_off" else "turn_on"
        state == "opening" || state == "closing" -> "stop_cover"
        on -> "close_cover"
        else -> "open_cover"
    }
    val ids = JsonArray(states.map { JsonPrimitive(it.entityId) })
    return CardAction.CallService(domain, service, JsonObject(mapOf("entity_id" to ids)), target = null)
}

/**
 * Port of `domainIcon` with `ha-domain-icon`'s fallback: the domain's icon translation for [deviceClass] (or its
 * default) in [state], else the domain's built-in icon.
 */
private fun HassSnapshot.domainIcon(domain: String, deviceClass: String?, state: String): String {
    val component = icons.entityComponent.obj(domain)?.takeIf { domain in config.components }
    val translations = deviceClass?.let { component?.obj(it) } ?: component?.obj(DEFAULT_TRANSLATION)
    return translations?.let { iconFromTranslations(state, it) } ?: fallbackDomainIcon(domain)
}

private const val AREA_CONTROLS = "area-controls"
private const val COVER = "cover"
private const val DEFAULT_TRANSLATION = "_"
private const val MAX_DEFAULT_AREA_CONTROLS = 4
private val COVER_GROUP_STATES = listOf("opening", "closing", "open")

/** `AREA_CONTROL_DOMAINS`, in upstream's order. */
private val AREA_CONTROL_DOMAINS = listOf(
    "light",
    "fan",
    "cover-shutter",
    "cover-blind",
    "cover-curtain",
    "cover-shade",
    "cover-awning",
    "cover-garage",
    "cover-gate",
    "cover-door",
    "cover-window",
    "cover-damper",
    "switch",
)
