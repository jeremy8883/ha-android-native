package io.homeassistant.companion.android.dashboard.quickbar

import io.homeassistant.companion.android.dashboard.derive.fallbackDomainIcon
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo

// The quick bar's items ("Search Home Assistant"). Port of the item model of `ha-quick-bar` (frontend@20260624.6
// src/dialogs/quick-bar/ha-quick-bar.ts) and `src/data/quick_bar.ts`.

/** The quick bar's sections, in the order of its filter chips (`QuickBarSection`). */
enum class QuickBarSection(val key: String) {
    NAVIGATE("navigate"),
    COMMAND("command"),
    ENTITY("entity"),
    DEVICE("device"),
    AREA("area"),
}

/**
 * An item the quick bar offers: [primary] over [secondary], [domainName] at the end, its [icon], and what choosing it
 * does ([action]); [searchLabels] and [sortingLabel] are what searching and ordering read.
 */
data class QuickBarItem(
    val id: String,
    val section: QuickBarSection,
    val primary: String,
    val secondary: String?,
    val icon: QuickBarIcon,
    val action: QuickBarAction,
    val domainName: String? = null,
    val searchLabels: Map<String, String?> = emptyMap(),
    val sortingLabel: String? = null,
)

/** What an item shows before its name (`_renderRow`). */
sealed interface QuickBarIcon {
    /** The entity's state badge (`state-badge`). */
    data class Entity(val entityId: String) : QuickBarIcon

    /** The integration's logo from the brands API (`ha-domain-icon` without an icon for the domain). */
    data class Brand(val domain: String) : QuickBarIcon

    /** An icon, [icon] an `mdi:` name or an SVG path on MDI's 24 × 24 viewport (see [isSvgPath]). */
    data class Plain(val icon: String) : QuickBarIcon

    /**
     * An icon in white on a circle of [color] (a page of Settings); an SVG path drawn [viewBoxTop] units down its
     * viewport's top (its `iconViewBox`).
     */
    data class Colored(val icon: String, val color: String, val viewBoxTop: Int = 0) : QuickBarIcon
}

/** Whether an item's [icon] is an SVG path (a logo the frontend draws itself) rather than an `mdi:` name. */
fun isSvgPath(icon: String): Boolean = !icon.startsWith(MDI_PREFIX)

/** What choosing an item does. */
sealed interface QuickBarAction {
    /** Go to [path], in the app or in the frontend (`navigate`). */
    data class Navigate(val path: String) : QuickBarAction

    /** Show the entity's more-info dialog (`hass-more-info`). */
    data class MoreInfo(val entityId: String) : QuickBarAction

    /**
     * Call `domain.service`, after the user confirms it when [confirm] (restarting or stopping the server), with
     * the frontend's [confirm] texts under `ui.dialogs.restart.<service>`.
     */
    data class CallService(val domain: String, val service: String, val confirm: Boolean) : QuickBarAction

    /** Show the keyboard shortcuts (`showShortcutsDialog`). */
    data object ShowShortcuts : QuickBarAction
}

/**
 * What the quick bar's items are drawn from: [hass], the server's [panels] and each config entry's integration
 * ([configEntryDomains], by entry id) for devices and the Bluetooth page. With [nativeOnly] (when other pages can't
 * be opened), only the items handled here are offered: no device, area or page that isn't shown natively.
 */
class QuickBarSource(
    val hass: HassSnapshot,
    val panels: Map<String, PanelInfo>,
    val configEntryDomains: Map<String, String>,
    val nativeOnly: Boolean = false,
) {
    /** Whether the user is an admin: only admins see commands, devices and areas. */
    val isAdmin: Boolean get() = hass.user?.isAdmin == true

    /**
     * Port of `ha-domain-icon` with `brand-fallback`: the domain's icon from its icon translations (when loaded), else
     * its built-in icon, else the integration's logo.
     */
    internal fun domainIcon(domain: String): QuickBarIcon {
        val component = hass.icons.entityComponent.obj(domain)?.takeIf { domain in hass.config.components }
        val icon = component?.obj(DEFAULT_TRANSLATION)?.string("default")
            ?: fallbackDomainIcon(domain).takeIf { it != DEFAULT_DOMAIN_ICON }
        return icon?.let { QuickBarIcon.Plain(it) } ?: QuickBarIcon.Brand(domain)
    }
}

private const val MDI_PREFIX = "mdi:"
private const val DEFAULT_TRANSLATION = "_"

/** `DEFAULT_DOMAIN_ICON`, which `fallbackDomainIcon` gives for a domain without a built-in icon. */
private const val DEFAULT_DOMAIN_ICON = "mdi:bookmark"
