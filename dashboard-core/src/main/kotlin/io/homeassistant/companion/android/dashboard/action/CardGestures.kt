package io.homeassistant.companion.android.dashboard.action

import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The gestures an interactive element of a card responds to, and the config [resolveAction] runs them with.
 *
 * @property config `entity` plus the `tap_action`, `hold_action` and `double_tap_action` to run
 */
data class ElementActions(val config: JsonObject, val tap: Boolean, val hold: Boolean, val doubleTap: Boolean) {
    /** Whether the element responds to any gesture. */
    val interactive: Boolean get() = tap || hold || doubleTap
}

/** The interactive parts of a card: the card itself, and the icon for tiles. */
data class CardActions(val card: ElementActions, val icon: ElementActions?)

/**
 * How [card] reacts to gestures. Tiles default to more-info on tap and toggle (for toggleable domains) on the
 * icon, as `hui-tile-card.setConfig` does (frontend@20260624.6 src/panels/lovelace/cards/hui-tile-card.ts);
 * other cards react only to the actions they configure.
 */
fun cardActions(card: CardConfig): CardActions {
    val json = card.json
    if (card.type != TILE) return CardActions(elementActions(json, tapWhenUnset = false), icon = null)

    val entity = json.string("entity").orEmpty()
    val defaults = buildJsonObject {
        put("tap_action", buildJsonObject { put("action", "more-info") })
        put("icon_tap_action", buildJsonObject { put("action", defaultTileIconAction(entity)) })
    }
    val config = JsonObject(defaults + json)
    val iconConfig = JsonObject(
        buildMap<String, JsonElement> {
            json["entity"]?.let { put("entity", it) }
            config["icon_tap_action"]?.let { put("tap_action", it) }
            config["icon_hold_action"]?.let { put("hold_action", it) }
            config["icon_double_tap_action"]?.let { put("double_tap_action", it) }
        },
    )
    return CardActions(
        card = elementActions(config, tapWhenUnset = true),
        icon = elementActions(iconConfig, tapWhenUnset = true),
    )
}

/** `!config.tap_action || hasAction(tap_action)` for tap when [tapWhenUnset], `hasAction` for the others. */
private fun elementActions(config: JsonObject, tapWhenUnset: Boolean) = ElementActions(
    config = config,
    tap = (tapWhenUnset && config[Gesture.TAP.configKey] == null) || hasAction(config, Gesture.TAP),
    hold = hasAction(config, Gesture.HOLD),
    doubleTap = hasAction(config, Gesture.DOUBLE_TAP),
)

/** Port of `getEntityDefaultTileIconAction`. */
private fun defaultTileIconAction(entityId: String): String {
    val domain = entityId.substringBefore('.')
    return if (domain in DOMAINS_TOGGLE || domain in setOf("button", "input_button", "scene")) "toggle" else "none"
}

private const val TILE = "tile"

/** Port of `DOMAINS_TOGGLE` (src/common/const.ts). */
private val DOMAINS_TOGGLE =
    setOf("fan", "input_boolean", "light", "switch", "group", "automation", "humidifier", "valve")
