package io.homeassistant.companion.android.dashboard.navigation

import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A panel registered on the server (`get_panels`). */
data class PanelInfo(
    val urlPath: String,
    val componentName: String?,
    val title: String?,
    val icon: String?,
    val showInSidebar: Boolean,
    val defaultVisible: Boolean,
    val requireAdmin: Boolean,
    /** Whether the legacy overview has a stored config (`config` of the `lovelace` panel). */
    val hasConfig: Boolean,
)

/** Decode a `get_panels` result, keeping the server's order. */
fun parsePanels(result: JsonObject): Map<String, PanelInfo> = result.mapNotNull { (key, value) ->
    val panel = value as? JsonObject ?: return@mapNotNull null
    val urlPath = panel.string("url_path") ?: key
    urlPath to PanelInfo(
        urlPath = urlPath,
        componentName = panel.string("component_name"),
        title = panel.string("title"),
        icon = panel.string("icon"),
        showInSidebar = panel.boolean("show_in_sidebar") != false,
        defaultVisible = panel.boolean("default_visible") != false,
        requireAdmin = panel.boolean("require_admin") == true,
        hasConfig = panel["config"] is JsonObject,
    )
}.toMap()

/** The user's sidebar customisation (`frontend/get_user_data` `sidebar`): their order and hidden panels. */
data class SidebarSettings(val panelOrder: List<String>, val hiddenPanels: List<String>) {
    companion object {
        val DEFAULT = SidebarSettings(emptyList(), emptyList())

        /** From the `value` of the `sidebar` user data; missing parts are empty, like upstream's fallback. */
        fun fromUserData(value: JsonObject?): SidebarSettings = SidebarSettings(
            panelOrder = (value?.get("panelOrder") as? JsonArray)?.mapNotNull { it.stringOrNull }.orEmpty(),
            hiddenPanels = (value?.get("hiddenPanels") as? JsonArray)?.mapNotNull { it.stringOrNull }.orEmpty(),
        )
    }
}

/** An entry of the navigation sidebar. */
data class SidebarItem(val urlPath: String, val title: String, val icon: String)

/**
 * The panels the sidebar lists, in order: the default panel first, then the user's order, then dashboards by
 * title, the built-in panels (energy, map, logbook, history) and the rest by title. Hidden panels, panels without
 * a title or not shown in the sidebar are left out, except the default panel. Settings, the profile and
 * notifications are fixed entries apart from this list.
 *
 * Ports of `computePanels`, `panelSorter`, `getPanelTitle` and `getPanelIcon` (frontend@20260624.6
 * src/components/ha-sidebar.ts, src/data/panel.ts). Panel icons upstream draws from built-in paths are given as
 * their `mdi:` names.
 */
fun sidebarItems(
    panels: Map<String, PanelInfo>,
    defaultPanel: String,
    settings: SidebarSettings,
    localize: Localize,
    locale: Locale,
): List<SidebarItem> {
    val listed = panels.values.filter { panel ->
        panel.urlPath !in FIXED_PANELS &&
            (
                panel.urlPath == defaultPanel ||
                    (
                        !panel.title.isNullOrEmpty() &&
                            panel.showInSidebar &&
                            panel.urlPath !in settings.hiddenPanels &&
                            (panel.defaultVisible || panel.urlPath in settings.panelOrder)
                        )
                )
    }
    val collator = Collator.getInstance(locale)
    val reverseOrder = settings.panelOrder.reversed()
    val sorted = listed.sortedWith { a, b ->
        val indexA = reverseOrder.indexOf(a.urlPath)
        val indexB = reverseOrder.indexOf(b.urlPath)
        if (indexA != indexB) {
            if (indexA < indexB) 1 else -1
        } else {
            defaultOrder(defaultPanel, a, b, collator)
        }
    }
    return sorted.map { panel ->
        SidebarItem(
            urlPath = panel.urlPath,
            title = panelTitle(panel, localize).orEmpty(),
            icon = PANEL_ICONS[panel.urlPath] ?: panel.icon?.ifEmpty { null }
                ?: (if (panel.componentName == "profile") "mdi:account" else null) ?: DEFAULT_ICON,
        )
    }
}

/** Port of `defaultPanelSorter`. */
private fun defaultOrder(defaultPanel: String, a: PanelInfo, b: PanelInfo, collator: Collator): Int {
    val aLovelace = a.componentName == LOVELACE
    val bLovelace = b.componentName == LOVELACE
    return when {
        a.urlPath == defaultPanel -> -1
        b.urlPath == defaultPanel -> 1
        aLovelace && bLovelace -> collator.compare(a.title.orEmpty(), b.title.orEmpty())
        aLovelace -> -1
        bLovelace -> 1
        a.urlPath in BUILT_IN_ORDER && b.urlPath in BUILT_IN_ORDER ->
            BUILT_IN_ORDER.getValue(a.urlPath) - BUILT_IN_ORDER.getValue(b.urlPath)
        a.urlPath in BUILT_IN_ORDER -> -1
        b.urlPath in BUILT_IN_ORDER -> 1
        else -> collator.compare(a.title.orEmpty(), b.title.orEmpty())
    }
}

/** Port of `getPanelTitle`: the translated panel name, else its title. */
fun panelTitle(panel: PanelInfo, localize: Localize): String? {
    val key = if (panel.urlPath == PROFILE ||
        panel.urlPath == NOT_FOUND
    ) {
        "panel.${panel.urlPath}"
    } else {
        "panel.${panel.title}"
    }
    return localize(key).ifEmpty { null } ?: panel.title?.ifEmpty { null }
}

/**
 * Port of `getDefaultPanelUrlPath`: the user's default panel, else the system's, else `home`; the legacy
 * `lovelace` overview only when it still has a stored config.
 *
 * @param userData the `value` of `frontend/get_user_data` `core`
 * @param systemData the `value` of `frontend/get_system_data` `core`
 */
fun defaultPanelUrlPath(userData: JsonObject?, systemData: JsonObject?, panels: Map<String, PanelInfo>): String {
    val panel = userData?.string("default_panel")?.ifEmpty { null }
        ?: systemData?.string("default_panel")?.ifEmpty { null }
        ?: HOME
    return if (panel == LOVELACE && panels[LOVELACE]?.hasConfig != true) HOME else panel
}

private const val HOME = "home"
private const val LOVELACE = "lovelace"
private const val PROFILE = "profile"
private const val NOT_FOUND = "notfound"
private const val DEFAULT_ICON = "mdi:view-dashboard"

/** Port of `FIXED_PANELS`: entries the sidebar shows separately. */
private val FIXED_PANELS = setOf(PROFILE, "config", NOT_FOUND)

/** Port of `SORT_VALUE_URL_PATHS`. */
private val BUILT_IN_ORDER = mapOf("energy" to 1, "map" to 2, "logbook" to 3, "history" to 4)

/** Port of `PANEL_ICON_PATHS`, as icon names. */
private val PANEL_ICONS = mapOf(
    "calendar" to "mdi:calendar",
    "energy" to "mdi:lightning-bolt",
    "history" to "mdi:chart-box",
    "logbook" to "mdi:format-list-bulleted-type",
    "map" to "mdi:tooltip-account",
    PROFILE to "mdi:account",
    "media-browser" to "mdi:play-box-multiple",
    "todo" to "mdi:clipboard-list",
)
