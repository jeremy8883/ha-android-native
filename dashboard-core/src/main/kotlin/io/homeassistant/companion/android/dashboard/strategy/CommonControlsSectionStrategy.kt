package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A section of the user's pinned entities followed by the entities they control most often, as tiles.
 * Port of `CommonControlsSectionStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/usage_prediction/common-controls-section-strategy.ts).
 *
 * @param predicted the `usage_prediction/common_control` entities, or `null` when they could not be fetched
 */
fun HassSnapshot.commonControlsSection(config: JsonObject, predicted: List<String>?): JsonObject {
    val cards = mutableListOf<JsonObject>()
    config.obj("heading")?.let(cards::add) ?: config.string("title")?.let { title ->
        cards += buildJsonObject {
            put("type", "heading")
            put("heading", title)
            config["icon"]?.let { put("icon", it) }
            config["title_visibilty"]?.let { put("visibility", it) }
        }
    }

    val limit = config.number("limit")?.toInt() ?: DEFAULT_LIMIT
    val included = config.stringList("include_entities").filter { it in states }
    val excluded = config.stringList("exclude_entities")
    val predictedEntities = predicted.orEmpty().filter { entityId ->
        entityId in states &&
            registries.entities[entityId]?.hidden != true &&
            entityId !in excluded &&
            entityId !in included
    }
    val entities = (included + predictedEntities).take(limit)
    return when {
        // Pinned entities already fill the section
        included.size >= limit -> gridSection(cards + included.take(limit).map(::commonControlTile))
        "usage_prediction" !in this.config.components ->
            disabledMessage(cards, "ui.panel.lovelace.strategy.common_controls.not_loaded", config)
        entities.isEmpty() -> disabledMessage(cards, "ui.panel.lovelace.strategy.common_controls.no_data", config)
        else -> gridSection(cards + entities.map(::commonControlTile))
    }
}

/** Port of `toTileCard`. */
fun commonControlTile(entityId: String): JsonObject = buildJsonObject {
    put("type", "tile")
    put("entity", entityId)
    putJsonArray("state_content") {
        add("state")
        add("area_name")
    }
    put("show_entity_picture", true)
}

private fun HassSnapshot.disabledMessage(cards: List<JsonObject>, key: String, config: JsonObject): JsonObject {
    val message = buildJsonObject {
        put("type", "markdown")
        put("content", localize(key))
    }
    return buildJsonObject {
        put("type", "grid")
        put("cards", JsonArray(cards + message))
        config.boolean("hide_empty")?.let { put("disabled", it) }
    }
}

private fun gridSection(cards: List<JsonObject>) = buildJsonObject {
    put("type", "grid")
    put("cards", JsonArray(cards))
}

private fun JsonObject.stringList(key: String): List<String> = array(key)?.mapNotNull { it.stringOrNull }.orEmpty()

private const val DEFAULT_LIMIT = 8
