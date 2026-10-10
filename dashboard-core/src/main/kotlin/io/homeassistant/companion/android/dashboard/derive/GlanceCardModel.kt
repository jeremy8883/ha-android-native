package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.history.HVAC_ACTION_TO_MODE
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A glance card: an optional [title], then a column per entity with its name, icon (or picture) and state, [columns]
 * to a row.
 * Port of `hui-glance-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-glance-card.ts) with
 * `state-badge`'s icon colour (src/components/entity/state-badge.ts).
 */
data class GlanceCardModel(val title: String?, val columns: Int, val entities: List<GlanceEntity>)

/**
 * One entity of a glance card; [missing] when it doesn't exist (a warning shows in its place).
 *
 * @property picture the entity's picture, shown instead of the icon
 */
data class GlanceEntity(
    val entityId: String,
    val name: String?,
    val icon: String?,
    val picture: String?,
    val color: DisplayColor?,
    val state: String?,
    val actions: ElementActions,
    val missing: Boolean,
    val brightness: Double? = null,
    val unavailable: Boolean = false,
)

/** The glance card of [card] at [now] (for relative times). */
fun HassSnapshot.glanceCardModel(card: CardConfig, now: Instant): GlanceCardModel {
    val json = card.json
    val options = GlanceOptions(
        name = json.boolean("show_name") != false,
        icon = json.boolean("show_icon") != false,
        state = json.boolean("show_state") != false,
        stateColor = json.boolean("state_color") != false,
    )
    val entities = (json["entities"] as? JsonArray).orEmpty().mapNotNull { entry ->
        when (entry) {
            is JsonObject -> entry
            is JsonPrimitive -> JsonObject(mapOf("entity" to entry))
            else -> null
        }?.takeIf { it.string("entity") != null }
    }
    return GlanceCardModel(
        title = json.string("title")?.ifEmpty { null },
        // `--glance-column-width`, a fifth of the width by default
        columns = number(json["columns"])?.toInt()?.takeIf { it > 0 } ?: DEFAULT_COLUMNS,
        entities = entities.map { glanceEntity(it, options, now) },
    )
}

private data class GlanceOptions(val name: Boolean, val icon: Boolean, val state: Boolean, val stateColor: Boolean)

private fun HassSnapshot.glanceEntity(config: JsonObject, options: GlanceOptions, now: Instant): GlanceEntity {
    val entityId = config.string("entity").orEmpty()
    // Each entity opens its details on hold unless configured otherwise
    val actions = elementActions(
        JsonObject(
            buildJsonObject { put("hold_action", buildJsonObject { put("action", "more-info") }) } + config,
        ),
        tapWhenUnset = true,
    )
    val state = states[entityId]
        ?: return GlanceEntity(
            entityId,
            null,
            null,
            null,
            null,
            entityId.takeIf {
                options.state
            },
            actions,
            missing = true,
        )
    val picture = (state.attributes.string("entity_picture_local") ?: state.attributes.string("entity_picture"))
        ?.takeIf { config.string("icon") == null }
    val colored = config.boolean("state_color") ?: options.stateColor
    return GlanceEntity(
        entityId = entityId,
        name = entityNameDisplay(state, config["name"]).takeIf { options.name },
        icon = entityIcon(entityId, configIcon = config.string("icon")).takeIf { options.icon },
        picture = (config.string("image") ?: picture)?.takeIf { options.icon },
        color = if (colored) badgeColor(state)?.withInactiveUnset() else null,
        state = glanceState(state, config, now).takeIf { options.state && config.boolean("show_state") != false },
        actions = actions,
        missing = false,
        brightness = iconBrightness(state).takeIf { colored },
        unavailable = state.state == STATE_UNAVAILABLE,
    )
}

/** Timestamps as how long ago, the last change when asked, else the state. */
private fun HassSnapshot.glanceState(state: EntityState, config: JsonObject, now: Instant): String {
    val timestamp = state.domain in TIMESTAMP_STATE_DOMAINS ||
        (state.domain == "sensor" && state.attributes.string("device_class") in SENSOR_TIMESTAMP_CLASSES)
    val known = state.state != STATE_UNAVAILABLE && state.state != STATE_UNKNOWN
    val at = when {
        timestamp && known -> parseJsDate(state.state, formats.zone)
        config.boolean("show_last_changed") == true -> Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong())
        else -> null
    }
    return at?.let { formats.relativeTime(minOf(it, now), now).replaceFirstChar { c -> c.uppercaseChar() } }
        ?: formatEntityState(state)
}

/** Port of `state-badge`'s colour: the state's, a light's own, or a climate's action's. */
internal fun badgeColor(state: EntityState): DisplayColor? {
    val action = state.attributes.string("hvac_action")
    val rgb = (state.attributes["rgb_color"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
    return when {
        action != null -> HVAC_ACTION_TO_MODE[action]?.let { stateColor(state, it) }
        rgb != null -> DisplayColor.Literal("rgb(${rgb.joinToString(",")})")
        else -> stateColor(state)
    }
}

private const val MILLIS = 1000.0
private const val DEFAULT_COLUMNS = 5
private val SENSOR_TIMESTAMP_CLASSES = setOf("timestamp", "uptime")
