package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string

/** Display-ready content of a shortcut card: drawn like a tile with a label, description and icon. */
data class ShortcutModel(
    val label: String,
    val description: String?,
    val icon: String,
    val color: DisplayColor?,
    val vertical: Boolean,
)

/**
 * Derive a shortcut card. Without a `label` or `icon`, they come from what the tap action leads to: an area view
 * shows the area, a URL its address, Assist its name; other targets get a link icon. Ports of `HuiShortcutCard`
 * and `getShortcutCardDefaults` with the area branch of `computeNavigationPathInfo` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-shortcut-card.ts, src/data/compute-navigation-path-info.ts). Panel and service
 * names are not resolved yet, so those fall back to the path.
 */
fun HassSnapshot.shortcutModel(card: CardConfig): ShortcutModel {
    val (defaultLabel, defaultIcon) = shortcutDefaults(card)
    return ShortcutModel(
        label = card.json.string("label")?.ifEmpty { null } ?: defaultLabel,
        description = card.json.string("description")?.ifEmpty { null },
        icon = card.json.string("icon")?.ifEmpty { null } ?: defaultIcon,
        color = card.json.string("color")?.ifEmpty { null }?.let(::cssColor),
        vertical = card.json.boolean("vertical") == true,
    )
}

private fun HassSnapshot.shortcutDefaults(card: CardConfig): Pair<String, String> {
    val action = card.json.obj("tap_action")
    return when (action?.string("action")) {
        "navigate" -> navigationDefaults(action.string("navigation_path").orEmpty())
        "assist" -> localize("ui.panel.lovelace.editor.action-editor.actions.assist") to "mdi:microphone"
        "url" -> action.string("url_path").orEmpty() to "mdi:open-in-new"
        "perform-action", "call-service" ->
            (action.string("perform_action") ?: action.string("service")).orEmpty() to "mdi:room-service"
        else -> "" to LINK_ICON
    }
}

/** The area branch of `computeNavigationPathInfo`; other paths show the panel's url path for now. */
private fun HassSnapshot.navigationDefaults(path: String): Pair<String, String> {
    val segments = path.removePrefix("/").split('/', '?')
    val areaId = when {
        segments.getOrNull(0) == "config" && segments.getOrNull(1) == "areas" && segments.getOrNull(2) == "area" ->
            segments.getOrNull(3)
        segments.getOrNull(1)?.startsWith(AREA_VIEW_PREFIX) == true -> segments[1].removePrefix(AREA_VIEW_PREFIX)
        // A view of this dashboard, like the home strategy's own links
        segments.size == 1 && segments[0].startsWith(AREA_VIEW_PREFIX) -> segments[0].removePrefix(AREA_VIEW_PREFIX)
        else -> null
    }
    if (areaId != null) {
        val area = registries.areas[areaId]
        return (area?.name?.ifEmpty { null } ?: areaId) to (area?.icon?.ifEmpty { null } ?: AREA_ICON)
    }
    return segments.firstOrNull().orEmpty() to LINK_ICON
}

private const val AREA_VIEW_PREFIX = "areas-"
private const val LINK_ICON = "mdi:link"
private const val AREA_ICON = "mdi:texture-box"
