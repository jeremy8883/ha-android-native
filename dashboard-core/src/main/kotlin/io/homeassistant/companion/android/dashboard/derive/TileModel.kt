package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.StateDisplayOptions
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.feature.tileFeatures
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant

/** Display-ready content of a tile card. */
data class TileModel(
    val entityId: String,
    val name: String,
    /** The secondary line (formatted state, brightness, relative time...), or `null` when hidden (`hide_state`). */
    val state: String?,
    /** The `mdi:` icon to show. */
    val icon: String?,
    val active: Boolean,
    val available: Boolean,
    /** Whether the icon and text are stacked, following the tile card's `vertical` option. */
    val vertical: Boolean = false,
    /** The controls under the tile, see [tileFeatures]. */
    val features: List<TileFeature> = emptyList(),
)

/**
 * Derive a tile from its config and the snapshot, or `null` when the entity does not exist.
 * Follows `hui-tile-card` render (frontend@20260624.6 src/panels/lovelace/cards/hui-tile-card.ts): the name from
 * the `name` option, the icon as `ha-state-icon` picks it, and the secondary line from `state-display` with the
 * `state_content` and `time_format` options.
 *
 * @param now the time relative times are shown against
 */
fun HassSnapshot.tileModel(card: CardConfig, now: Instant): TileModel? {
    val entity = card.entity?.let(states::get) ?: return null
    val entityId = entity.entityId
    val hideState = card.json.boolean("hide_state") == true
    return TileModel(
        entityId = entityId,
        name = entityNameDisplay(entity, card.json["name"]),
        state = if (hideState) {
            null
        } else {
            stateDisplay(
                entity,
                card.json["state_content"],
                now,
                StateDisplayOptions(timeFormat = card.json.string("time_format")),
            )
        },
        icon = entityIcon(entityId, configIcon = card.json.string("icon")),
        active = entity.isActive(),
        available = entity.state != STATE_UNAVAILABLE,
        vertical = card.json.boolean("vertical") == true,
        features = tileFeatures(card),
    )
}
