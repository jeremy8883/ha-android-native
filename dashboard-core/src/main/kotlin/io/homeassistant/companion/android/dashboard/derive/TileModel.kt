package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string

/** Display-ready content of a tile card. */
data class TileModel(
    val entityId: String,
    val name: String,
    val state: String,
    val unit: String?,
    val icon: String?,
    val active: Boolean,
    val available: Boolean,
)

/**
 * Derive a tile from its config and the current states, or `null` when the entity does not exist.
 *
 * Basic version: the name is the config `name` or `friendly_name`, and the state is shown raw with its unit.
 * Still to port from frontend@20260624.6: entity naming (src/common/entity/compute_entity_name.ts) and
 * translated state display (src/common/entity/compute_state_display.ts).
 */
fun tileModel(card: CardConfig, states: EntityStates): TileModel? {
    val entityId = card.entity ?: return null
    val entity = states[entityId] ?: return null
    return TileModel(
        entityId = entityId,
        name = card.json.string("name") ?: entity.attributes.string("friendly_name") ?: entityId,
        state = entity.state,
        unit = entity.attributes.string("unit_of_measurement"),
        icon = card.json.string("icon") ?: entity.attributes.string("icon"),
        active = entity.isActive(),
        available = entity.state != STATE_UNAVAILABLE,
    )
}
