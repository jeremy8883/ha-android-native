package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The calls and registry updates of covers' and valves' favourite positions (frontend@20260624.6
// src/dialogs/more-info/components/covers/, valves/, and the numeric favourites handler of favorites.ts).

/** The call that sets [state]'s [kind] to [value], as tapping a favourite does. */
fun positionCall(state: EntityState, kind: PositionKind, value: Double): CardAction.CallService {
    val (service, key) = when {
        state.domain == VALVE -> "set_valve_position" to "position"
        kind == PositionKind.Tilt -> "set_cover_tilt_position" to "tilt_position"
        else -> "set_cover_position" to "position"
    }
    return CardAction.CallService(
        state.domain,
        service,
        JsonObject(entityData(state) + (key to jsonNumber(value))),
        null,
    )
}

/**
 * The command that saves [values] as [state]'s favourites of [kind], keeping its other options and the other
 * kind's favourites as shown (upstream's `_save`).
 */
fun positionFavoritesUpdate(
    state: EntityState,
    entry: JsonObject,
    kind: PositionKind,
    values: List<Double>,
): WsCommand {
    val options = entry.obj("options")?.obj(state.domain).orEmpty().toMutableMap()
    favoriteKinds(state).forEach { shownKind ->
        options[optionKey(shownKind)] =
            numbers(favoriteValues(state, entry, shownKind))
    }
    options[optionKey(kind)] = numbers(normalizeFavoritePositions(values))
    return update(state.entityId, state.domain, JsonObject(options))
}

/** The command that resets [state]'s favourites to upstream's defaults (`getResetOptions`, which JSON leaves empty). */
fun positionFavoritesReset(state: EntityState): WsCommand = update(state.entityId, state.domain, JsonObject(emptyMap()))

/** The commands that copy [state]'s favourites to each of [targets] (the numeric favourites handler's `copy`). */
fun positionFavoritesCopy(state: EntityState, entry: JsonObject, targets: List<String>): List<WsCommand> {
    val options =
        JsonObject(favoriteKinds(state).associate { optionKey(it) to numbers(favoriteValues(state, entry, it)) })
    return targets.map { update(it, state.domain, options) }
}

/** The entities [state]'s favourites can be copied to: those of its domain with every position kind it has. */
fun HassSnapshot.positionFavoriteCopyTargets(state: EntityState): List<String> = states.values.filter { candidate ->
    candidate.entityId != state.entityId &&
        candidate.domain == state.domain &&
        candidate.entityId in registries.entities &&
        favoriteKinds(state).all { it in favoriteKinds(candidate) }
}.map { it.entityId }

private fun numbers(values: List<Double>) = JsonArray(values.map(::jsonNumber))

private fun update(entityId: String, domain: String, options: JsonObject) = WsCommand(
    "config/entity_registry/update",
    JsonObject(
        mapOf(
            "entity_id" to JsonPrimitive(entityId),
            "options_domain" to JsonPrimitive(domain),
            "options" to options,
        ),
    ),
)
