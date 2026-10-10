package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string

/** The `navigation_path` of the card's tap action when it is a `navigate` action. */
val CardConfig.tapNavigationPath: String?
    get() = json.obj("tap_action")?.takeIf { it.string("action") == "navigate" }?.string("navigation_path")

/** Display-ready content of an area card: [color] is its icon's (`--tile-color`). */
data class AreaCardModel(val name: String, val icon: String?, val color: DisplayColor = STATE_ICON_COLOR)

/**
 * Basic area card: the area's name and icon, or `null` for an unknown area.
 * Still to port from frontend@20260624.6 src/panels/lovelace/cards/hui-area-card.ts: sensors, alerts, controls.
 */
fun HassSnapshot.areaCardModel(card: CardConfig): AreaCardModel? {
    val area = card.json.string("area")?.let(registries.areas::get) ?: return null
    return AreaCardModel(
        name = card.json.string("name") ?: area.name,
        icon = area.icon,
        // The configured colour, else `--tile-color: var(--state-icon-color)`
        color = card.json.string("color")?.ifEmpty { null }?.let(::cssColor) ?: STATE_ICON_COLOR,
    )
}
