package io.homeassistant.companion.android.dashboard.quickbar

import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.panelTitle
import io.homeassistant.companion.android.dashboard.panels.isNative

// The quick bar's Navigate section. Port of `generateNavigationCommands` (frontend@20260624.6 src/data/quick_bar.ts)
// with the pages of Settings it lists (`configSections`, src/panels/config/ha-panel-config.ts) and
// `filterNavigationPages` (src/common/config/filter_navigation_pages.ts).

/**
 * Port of `generateNavigationCommands`: every panel but the system ones, then the pages of Settings the user can see
 * (each path once), then the app store for admins on a supervised install, then "Shortcuts" (the keyboard shortcuts,
 * without a path); each "Navigate", ordered by name.
 *
 * Upstream also lists each installed app (with its logo, from the Supervisor), which isn't ported yet.
 */
fun QuickBarSource.navigationItems(): List<QuickBarItem> {
    val commands = panelCommands() + configPageCommands() + appStoreCommands() + shortcutsCommand()
    val secondary = hass.localize("$QUICK_BAR.commands.types.navigation")
    return commands.mapIndexed { index, command ->
        QuickBarItem(
            id = "navigation_${index}_${command.path}",
            section = QuickBarSection.NAVIGATE,
            primary = command.primary,
            secondary = secondary,
            icon = command.icon,
            // Upstream's item without a path opens the keyboard shortcuts
            action = if (command.path.isEmpty()) {
                QuickBarAction.ShowShortcuts
            } else {
                QuickBarAction.Navigate(
                    command.path,
                )
            },
            searchLabels = mapOf("primary" to command.primary, "path" to command.path, "secondary" to secondary),
            sortingLabel = "${command.primary}_$secondary",
        )
    }.filter { !nativeOnly || opensNatively(it) }.sortedBySortingLabel()
}

/** Whether what [item] opens is shown natively: the keyboard shortcuts, or a page whose panel the drawer shows. */
private fun QuickBarSource.opensNatively(item: QuickBarItem): Boolean = when (val action = item.action) {
    QuickBarAction.ShowShortcuts -> true
    is QuickBarAction.Navigate ->
        panels[action.path.removePrefix("/").substringBefore('/')]?.isNative() == true
    else -> false
}

/** Upstream's `additionalItems`: "Shortcuts", which opens the keyboard shortcuts. */
private fun QuickBarSource.shortcutsCommand(): NavigationCommand = NavigationCommand(
    path = "",
    primary = hass.localize("$QUICK_BAR.commands.navigation.shortcuts"),
    icon = QuickBarIcon.Plain(SHORTCUTS_ICON),
)

/** A page the Navigate section leads to (`BaseNavigationCommand`). */
private class NavigationCommand(val path: String, val primary: String, val icon: QuickBarIcon)

/** Port of `generateNavigationPanelCommands`: the panels but the system ones, by their title. */
private fun QuickBarSource.panelCommands(): List<NavigationCommand> = panels.values
    .filter { it.urlPath !in SYSTEM_PANELS }
    .map { panel ->
        NavigationCommand(
            path = "/${panel.urlPath}",
            primary = panelTitle(panel, hass.localize) ?: panel.urlPath,
            icon = QuickBarIcon.Plain(panelIcon(panel)),
        )
    }

/** Port of `getPanelIcon`, with the quick bar's fallback. */
private fun panelIcon(panel: PanelInfo): String = panel.icon?.ifEmpty { null }
    ?: PROFILE_ICON.takeIf { panel.componentName == PROFILE }
    ?: PANEL_FALLBACK_ICON

/**
 * Port of `generateNavigationConfigSectionCommands`: the pages of Settings the user can see, each path once, named
 * by `ui.dialogs.quick-bar.commands.navigation.<page>`.
 *
 * Upstream falls back to a page's own translation key, but those are in the Settings translations, which the
 * frontend loads only once Settings is opened; from a dashboard they are unknown, so those pages are left out.
 */
private fun QuickBarSource.configPageCommands(): List<NavigationCommand> {
    val commands = mutableListOf<NavigationCommand>()
    CONFIG_PAGES.filter { canShow(it) }.forEach { page ->
        val caption = hass.localize("$QUICK_BAR.commands.navigation.${page.name}")
        if (caption.isNotEmpty() && commands.none { it.path == page.path }) {
            val viewBoxTop = if (page.path == MATTER_PATH) MATTER_VIEW_BOX_TOP else 0
            commands += NavigationCommand(page.path, caption, QuickBarIcon.Colored(page.icon, page.color, viewBoxTop))
        }
    }
    return commands
}

/** Port of `filterNavigationPages` with `canShowPage`. */
private fun QuickBarSource.canShow(page: ConfigPage): Boolean = when {
    // Only the companion apps have their own settings screen
    page.path == EXTERNAL_APP_CONFIGURATION -> false
    // Every page but the companion app's settings is for admins (`adminOnly`)
    !isAdmin -> false
    BLUETOOTH in page.components -> BLUETOOTH in configEntryDomains.values
    else -> (page.core || page.components.isEmpty() || page.components.any { it in hass.config.components }) &&
        (page.hasDomain == null || hass.registries.entities.keys.any { it.startsWith("${page.hasDomain}.") })
}

/** The app store, for admins when the Supervisor is loaded. */
private fun QuickBarSource.appStoreCommands(): List<NavigationCommand> {
    if (!isAdmin || HASSIO !in hass.config.components) return emptyList()
    return listOf(
        NavigationCommand(
            path = "/config/apps/available",
            primary = hass.localize("$QUICK_BAR.commands.navigation.app_store"),
            icon = QuickBarIcon.Colored(APP_STORE_ICON, APP_COLOR),
        ),
    )
}

/**
 * A page of Settings (`PageNavigation`), for admins: shown when [core] or one of its [components] is loaded, and only
 * when an entity of [hasDomain] exists when set.
 */
private class ConfigPage(
    val path: String,
    val icon: String,
    val color: String,
    val components: List<String> = emptyList(),
    val core: Boolean = false,
    val hasDomain: String? = null,
) {
    /** The page's name in its path, which names it in the quick bar (`getNavigationInfoFromConfig`). */
    val name: String = path.removePrefix("/").substringAfter('/').substringBefore('/')
}

/** `configSections`, flattened in upstream's order. */
private val CONFIG_PAGES = listOf(
    ConfigPage("/config/integrations", "mdi:devices", "#0D47A1", core = true),
    ConfigPage("/config/automation", "mdi:robot", "#518C43", core = true),
    ConfigPage("/config/areas", "mdi:sofa", "#E48629", components = listOf("zone")),
    ConfigPage("/config/apps", "mdi:puzzle", "#F1C447", core = true),
    ConfigPage("/config/lovelace/dashboards", "mdi:view-dashboard", "#B1345C", components = listOf("lovelace")),
    ConfigPage("/config/voice-assistants", "mdi:microphone", "#3263C3"),
    ConfigPage("#external-app-configuration", "mdi:cellphone-cog", "#8E24AA"),
    ConfigPage("/config/matter", MATTER_ICON, "#2458B3", components = listOf("matter")),
    ConfigPage("/config/zha", "mdi:zigbee", "#E74011", components = listOf("zha")),
    ConfigPage("/config/zwave_js", "mdi:z-wave", "#153163", components = listOf("zwave_js")),
    ConfigPage("/knx", KNX_ICON, "#4EAA66", components = listOf("knx")),
    ConfigPage("/config/thread", THREAD_ICON, "#ED7744", components = listOf("thread")),
    ConfigPage("/config/bluetooth", "mdi:bluetooth", "#0082FC", components = listOf("bluetooth")),
    ConfigPage("/config/infrared", "mdi:remote", "#9C27B0", hasDomain = "infrared"),
    ConfigPage(
        "/config/radio-frequency",
        "mdi:radio-tower",
        "#E74011",
        components = listOf("radio_frequency"),
        hasDomain = "radio_frequency",
    ),
    ConfigPage("/insteon", INSTEON_ICON, "#E4002C", components = listOf("insteon")),
    ConfigPage("/config/tags", "mdi:nfc-variant", "#616161", components = listOf("tag")),
    ConfigPage("/config/person", "mdi:account", "#5A87FA", components = listOf("person", "users")),
    ConfigPage("/config/system", "mdi:cog", "#301ABE", core = true),
    ConfigPage("/config/developer-tools", "mdi:hammer", "#7A5AA6", core = true),
    ConfigPage("/config/info", "mdi:information-outline", "#4A5963", core = true),
    ConfigPage("/config/backup", "mdi:backup-restore", "#4084CD", components = listOf("backup")),
    ConfigPage("/config/integrations", "mdi:puzzle", "#2D338F", components = listOf("integrations"), core = true),
    ConfigPage("/config/devices", "mdi:devices", "#2D338F", components = listOf("devices"), core = true),
    ConfigPage("/config/entities", "mdi:shape", "#2D338F", components = listOf("entities"), core = true),
    ConfigPage("/config/helpers", "mdi:tools", "#4D2EA4", components = listOf("helpers"), core = true),
    ConfigPage("/config/automation", "mdi:robot", "#518C43", components = listOf("automation")),
    ConfigPage("/config/scene", "mdi:palette", "#518C43", components = listOf("scene")),
    ConfigPage("/config/script", "mdi:script-text", "#518C43", components = listOf("script")),
    ConfigPage("/config/blueprint", "mdi:palette-swatch", "#518C43", components = listOf("blueprint")),
    ConfigPage("/config/tags", "mdi:nfc-variant", "#616161", components = listOf("tag")),
    ConfigPage("/config/voice-assistants", "mdi:microphone", "#3263C3"),
    ConfigPage("/config/developer-tools", "mdi:hammer", "#7A5AA6", core = true),
    ConfigPage("/config/energy", "mdi:lightning-bolt", "#F1C447", components = listOf("energy")),
    ConfigPage("/config/dhcp", "mdi:network", "#B1345C", components = listOf("dhcp")),
    ConfigPage("/config/ssdp", "mdi:network", "#B1345C", components = listOf("ssdp")),
    ConfigPage("/config/zeroconf", "mdi:network", "#B1345C", components = listOf("zeroconf")),
    ConfigPage("/config/application_credentials", "mdi:puzzle", "#2D338F"),
    ConfigPage("/config/mqtt", "mdi:puzzle", "#2D338F", components = listOf("mqtt")),
    ConfigPage("/config/lovelace/dashboards", "mdi:view-dashboard", "#B1345C", components = listOf("lovelace")),
    ConfigPage("/config/person", "mdi:account", "#5A87FA", components = listOf("person")),
    ConfigPage("/config/users", "mdi:badge-account-horizontal", "#5A87FA", components = listOf("users"), core = true),
    ConfigPage("/config/areas", "mdi:sofa", "#2D338F", components = listOf("areas"), core = true),
    ConfigPage("/config/labels", "mdi:label", "#2D338F", components = listOf("labels"), core = true),
    ConfigPage("/config/zone", "mdi:map-marker-radius", "#E48629", components = listOf("zone")),
    ConfigPage("/config/general", "mdi:cog", "#653249", core = true),
    ConfigPage("/config/updates", "mdi:update", "#3B808E"),
    ConfigPage("/config/repairs", "mdi:screwdriver", "#5c995c"),
    ConfigPage("/config/logs", "mdi:text-box-outline", "#C65326", components = listOf("logs"), core = true),
    ConfigPage("/config/backup", "mdi:backup-restore", "#0D47A1", components = listOf("backup")),
    ConfigPage("/config/analytics", "mdi:shape", "#f1c447"),
    ConfigPage("/config/ai-tasks", "mdi:star-four-points", "#8B69E3", core = true),
    ConfigPage("/config/labs", "mdi:flask", "#b1b134", core = true),
    ConfigPage("/config/network", "mdi:network", "#B1345C"),
    ConfigPage("/config/storage", "mdi:database", "#518C43", components = listOf("hassio")),
    ConfigPage("/config/hardware", "mdi:memory", "#301A8E", components = listOf("hassio", "hardware")),
    ConfigPage("/config/info", "mdi:information-outline", "#4A5963", components = listOf("info"), core = true),
)

internal const val QUICK_BAR = "ui.dialogs.quick-bar"
private const val MATTER_PATH = "/config/matter"

/** The top of the Matter logo's `iconViewBox` (`0 1 24 24`). */
private const val MATTER_VIEW_BOX_TOP = 1
private const val EXTERNAL_APP_CONFIGURATION = "#external-app-configuration"
private const val BLUETOOTH = "bluetooth"
private const val HASSIO = "hassio"
private const val PROFILE = "profile"
private const val PROFILE_ICON = "mdi:account"
private const val PANEL_FALLBACK_ICON = "mdi:view-dashboard"
private const val APP_STORE_ICON = "mdi:store-plus"
private const val SHORTCUTS_ICON = "mdi:keyboard"
private const val APP_COLOR = "#F1C447"

/** `SYSTEM_PANELS`: the panels the quick bar never lists. */
private val SYSTEM_PANELS = setOf("_my_redirect", "notfound", "app")

private const val MATTER_ICON =
    "M7.228375 6.41685c0.98855 0.80195 2.16365 1.3412 3.416275 1.56765V1.30093l1.3612 -0.7854275 1.360125 0.7854275V7.9845c1.252875 -0.226675 2.4283 -0.765875 3.41735 -1.56765l2.471225 1.4293c-4.019075 3.976275 -10.490025 3.976275 -14.5091 0l2.482925 -1.4293Zm3.00335 17.067575c1.43325 -5.47035 -1.8052 -11.074775 -7.2604 -12.564675v2.859675c1.189125 0.455 2.244125 1.202875 3.0672 2.174275L0.25 19.2955v1.5719l1.3611925 0.781175L7.39865 18.3068c0.430175 1.19825 0.550625 2.48575 0.35015 3.743l2.482925 1.434625ZM21.034 10.91975c-5.452225 1.4932 -8.6871 7.09635 -7.254025 12.564675l2.47655 -1.43035c-0.200025 -1.257275 -0.079575 -2.544675 0.35015 -3.743025l5.7832 3.337525L23.75 20.86315V19.2955L17.961475 15.9537c0.8233 -0.97115 1.878225 -1.718975 3.0672 -2.174275l0.005325 -2.859675Z"

private const val KNX_ICON =
    "M 3.9861338,14.261456 3.7267552,13.934877 6.3179131,11.306266 H 4.466374 l -2.6385205,2.68258 V 11.312882 H 0.00440574 L 0,17.679803 l 1.8278535,5.43e-4 v -1.818482 l 0.7225444,-0.732459 2.1373588,2.543782 2.1869324,-5.44e-4 M 24,17.680369 21.809238,17.669359 19.885559,15.375598 17.640262,17.68037 h -1.828407 l 3.236048,-3.302138 -2.574075,-3.067547 2.135161,0.0016 1.610309,1.87687 1.866403,-1.87687 h 1.828429 l -2.857742,2.87478 m -10.589867,-2.924898 2.829625,3.990552 -0.01489,-3.977887 1.811889,-0.0044 0.0011,6.357564 -2.093314,-5.44e-4 -2.922133,-3.947594 -0.0314,3.947594 H 8.2581097 V 11.261677 M 11.971203,6.3517488 c 0,0 2.800714,-0.093203 6.172001,1.0812045 3.462393,1.0898845 5.770926,3.4695627 5.770926,3.4695627 l -1.823898,-5.43e-4 C 22.088532,10.900273 20.577938,9.4271528 17.660223,8.5024618 15.139256,7.703366 12.723057,7.645835 12.111178,7.6449876 l -9.71e-4,0.0011 c 0,0 -0.0259,-6.4e-4 -0.07527,-9.714e-4 -0.04726,3.33e-4 -0.07201,9.714e-4 -0.07201,9.714e-4 v -0.00113 C 11.337007,7.6453728 8.8132091,7.7001736 6.2821829,8.5024618 3.3627914,9.4276738 1.8521646,10.901973 1.8521646,10.901973 l -1.82398708,5.43e-4 C 0.03128403,10.899322 2.339143,8.5221038 5.799224,7.4329533 9.170444,6.2585642 11.971203,6.3517488 11.971203,6.3517488 Z"

private const val THREAD_ICON =
    "m 17.126982,8.0730792 c 0,-0.7297242 -0.593746,-1.32357 -1.323637,-1.32357 -0.729454,0 -1.323199,0.5938458 -1.323199,1.32357 v 1.3234242 l 1.323199,1.458e-4 c 0.729891,0 1.323637,-0.5937006 1.323637,-1.32357 z M 11.999709,0 C 5.3829818,0 0,5.3838955 0,12.001455 0,18.574352 5.3105455,23.927406 11.865164,24 V 12.012075 l -3.9275642,-2.91e-4 c -1.1669814,0 -2.1169453,0.949979 -2.1169453,2.118323 0,1.16718 0.9499639,2.116868 2.1169453,2.116868 v 2.615717 c -2.6093089,0 -4.732218,-2.12327 -4.732218,-4.732585 0,-2.61048 2.1229091,-4.7343308 4.732218,-4.7343308 l 3.9275642,5.82e-4 v -1.323279 c 0,-2.172296 1.766691,-3.9395777 3.938181,-3.9395777 2.171928,0 3.9392,1.7672817 3.9392,3.9395777 0,2.1721498 -1.767272,3.9395768 -3.9392,3.9395768 l -1.323199,-1.45e-4 V 23.744102 C 19.911127,22.597726 24,17.768833 24,12.001455 24,5.3838955 18.616727,0 11.999709,0 Z"

private const val INSTEON_ICON =
    "m 12.001571,6.3842473 h 0.02973 c 3.652189,0 6.767389,-2.29456 7.987462,-5.5177193 L 15.389382,0 Z m 0,0 h -0.02972 c -3.6522186,0 -6.7673314,-2.2918546 -7.9874477,-5.5177193 h -0.00271 L 8.6111273,0 Z M 6.3840436,11.999287 v -0.02972 c 0,-3.6524074 -2.2944727,-6.7675928 -5.51754469,-7.9877383 L 0,8.6114473 Z m 0,0 v 0.02964 c 0,3.652378 -2.2917818,6.767578 -5.51754469,7.987796 v 0.0026 L 0,15.389818 Z M 24,8.6114473 23.133527,3.9818327 v 0.00269 C 19.907636,5.2046836 17.616,8.3198691 17.616,11.972276 v 0.02966 0.02972 0.0027 c 0,3.65232 2.2944,6.76752 5.517527,7.987738 L 24,15.392436 17.616,12.001935 Z M 20.018618,23.133527 15.389091,24 11.99872,17.615709 h 0.02964 c 3.652218,0 6.767418,2.291927 7.987491,5.517818 z M 11.99872,17.615709 8.6082618,24 3.9788364,23.133527 C 5.1989527,19.9104 8.3140655,17.615709 11.966284,17.615709 h 0.0027 z"
