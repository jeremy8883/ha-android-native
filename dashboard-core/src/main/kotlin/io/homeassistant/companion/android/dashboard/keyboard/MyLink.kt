package io.homeassistant.companion.android.dashboard.keyboard

import java.net.URLEncoder

// The "m" shortcut's My Home Assistant link. Port of `_createMyLink` of `quick-bar-mixin` (frontend@20260624.6
// src/state/quick-bar-mixin.ts) with the redirects of `getMyRedirects` (src/panels/my/ha-panel-my.ts).

/** What creating a My link to a page gives. */
sealed interface MyLink {
    /** The page on my.home-assistant.io that creates the link, to open. */
    data class Create(val url: String) : MyLink

    /** No My link leads to [path] (`ui.notification_toast.no_matching_link_found`). */
    data class NotFound(val path: String) : MyLink
}

/**
 * Port of `_createMyLink`: the first redirect whose page [path] is in, with the parameters it takes from [query]
 * (the page's address's), and the integration of an integration's page.
 *
 * Upstream also adds an app page's repository, fetched from the Supervisor; app pages aren't shown here, so a link to
 * one is made without it.
 */
fun createMyLink(path: String, query: Map<String, String> = emptyMap()): MyLink {
    val redirect = MY_REDIRECTS.firstOrNull { path.startsWith(it.redirect) } ?: return MyLink.NotFound(path)
    val params = buildList {
        add(REDIRECT to redirect.slug)
        redirect.params.forEach { key -> query[key]?.let { add(key to it) } }
        // `targetPath.split("/")[n]`
        val segments = path.split("/")
        when (redirect.redirect) {
            INTEGRATION_PAGE -> add(DOMAIN to segments.getOrElse(INTEGRATION_SEGMENT) { UNDEFINED })
            APP_PAGE -> add(APP to segments.getOrElse(APP_SEGMENT) { UNDEFINED })
        }
    }
    // `URLSearchParams.toString()`: form encoded
    val encoded = params.joinToString("&") { (key, value) -> "${key.formEncoded()}=${value.formEncoded()}" }
    return MyLink.Create("$CREATE_LINK?$encoded")
}

private fun String.formEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8)

/** A My link's target: [slug] leads to the page at [redirect], passing on the query's [params]. */
private class MyRedirect(val slug: String, val redirect: String, vararg val params: String)

private const val CREATE_LINK = "https://my.home-assistant.io/create-link/"
private const val REDIRECT = "redirect"
private const val DOMAIN = "domain"
private const val APP = "app"
private const val INTEGRATION_PAGE = "/config/integrations/integration"
private const val INTEGRATION_SEGMENT = 4
private const val APP_PAGE = "/config/app"
private const val APP_SEGMENT = 3

/** What JavaScript appends for a missing path segment. */
private const val UNDEFINED = "undefined"

/** `getMyRedirects`, in upstream's order (the first match wins). */
private val MY_REDIRECTS = listOf(
    MyRedirect("application_credentials", "/config/application_credentials"),
    MyRedirect("developer_assist", "/config/developer-tools/assist"),
    MyRedirect("developer_debug", "/config/developer-tools/debug"),
    MyRedirect("developer_states", "/config/developer-tools/state"),
    MyRedirect("developer_services", "/config/developer-tools/action"),
    MyRedirect("developer_call_service", "/config/developer-tools/action", "service"),
    MyRedirect("developer_template", "/config/developer-tools/template"),
    MyRedirect("developer_events", "/config/developer-tools/event"),
    MyRedirect("developer_statistics", "/config/developer-tools/statistics"),
    MyRedirect("server_controls", "/config/developer-tools/yaml"),
    MyRedirect("calendar", "/calendar"),
    MyRedirect("companion_app", "#external-app-configuration"),
    MyRedirect("config", "/config/dashboard"),
    MyRedirect("cloud", "/config/cloud"),
    MyRedirect("config_flow_start", "/config/integrations/dashboard/add", "domain"),
    MyRedirect("brand", "/config/integrations/dashboard/add", "brand"),
    MyRedirect("integration", "/config/integrations/integration", "domain"),
    MyRedirect("integrations", "/config/integrations"),
    MyRedirect("config_mqtt", "/config/mqtt"),
    MyRedirect("config_zha", "/config/zha/dashboard"),
    MyRedirect("config_zwave_js", "/config/zwave_js/dashboard"),
    MyRedirect("config_matter", "/config/matter/dashboard"),
    MyRedirect("config_thread", "/config/thread"),
    MyRedirect("add_zigbee_device", "/config/zha/add"),
    MyRedirect("add_zwave_device", "/config/zwave_js/add"),
    MyRedirect("add_matter_device", "/config/matter/add"),
    MyRedirect("bluetooth_advertisement_monitor", "/config/bluetooth/advertisement-monitor"),
    MyRedirect("bluetooth_connection_monitor", "/config/bluetooth/connection-monitor"),
    MyRedirect("bluetooth_visualization", "/config/bluetooth/visualization"),
    MyRedirect("config_ai_task", "/config/ai-tasks"),
    MyRedirect("config_bluetooth", "/config/bluetooth"),
    MyRedirect("config_dhcp", "/config/dhcp"),
    MyRedirect("config_energy", "/config/energy"),
    MyRedirect("config_infrared", "/config/infrared"),
    MyRedirect("config_radiofrequency", "/config/radio-frequency"),
    MyRedirect("config_ssdp", "/config/ssdp"),
    MyRedirect("config_zeroconf", "/config/zeroconf"),
    MyRedirect("devices", "/config/devices/dashboard"),
    MyRedirect("entities", "/config/entities"),
    MyRedirect("labels", "/config/labels"),
    MyRedirect("energy", "/energy"),
    MyRedirect("areas", "/config/areas/dashboard"),
    MyRedirect("blueprint_import", "/config/blueprint/dashboard/import", "blueprint_url"),
    MyRedirect("blueprints", "/config/blueprint/dashboard"),
    MyRedirect("automations", "/config/automation/dashboard"),
    MyRedirect("scenes", "/config/scene/dashboard"),
    MyRedirect("scripts", "/config/script/dashboard"),
    MyRedirect("helpers", "/config/helpers"),
    MyRedirect("labs", "/config/labs", "domain", "preview_feature"),
    MyRedirect("tags", "/config/tags"),
    MyRedirect("voice_assistants", "/config/voice-assistants"),
    MyRedirect("lovelace_dashboards", "/config/lovelace/dashboards"),
    MyRedirect("lovelace_resources", "/config/lovelace/resources"),
    MyRedirect("oauth", "/auth/external/callback", "error", "code", "state"),
    MyRedirect("people", "/config/person"),
    MyRedirect("zones", "/config/zone"),
    MyRedirect("users", "/config/users"),
    MyRedirect("general", "/config/general"),
    MyRedirect("logs", "/config/logs", "provider"),
    MyRedirect("repairs", "/config/repairs"),
    MyRedirect("info", "/config/info"),
    MyRedirect("system_health", "/config/repairs?dialog=system-health"),
    MyRedirect("hardware", "/config/hardware"),
    MyRedirect("storage", "/config/storage"),
    MyRedirect("network", "/config/network"),
    MyRedirect("analytics", "/config/analytics"),
    MyRedirect("updates", "/config/updates"),
    MyRedirect("system_dashboard", "/config/system"),
    MyRedirect("customize", "/config/dashboard"),
    MyRedirect("profile_security", "/profile/security"),
    MyRedirect("profile", "/profile"),
    MyRedirect("logbook", "/logbook"),
    MyRedirect("history", "/history"),
    MyRedirect("maintenance", "/maintenance"),
    MyRedirect("overview", "/home/overview"),
    MyRedirect("media_browser", "/media-browser"),
    MyRedirect("backup_list", "/config/backup/backups"),
    MyRedirect("backup_config", "/config/backup/settings"),
    MyRedirect("backup", "/config/backup"),
    MyRedirect("supervisor_snapshots", "/config/backup"),
    MyRedirect("supervisor_backups", "/config/backup"),
    MyRedirect("supervisor_system", "/config/system"),
    MyRedirect("supervisor_logs", "/config/logs?provider=supervisor"),
    MyRedirect("supervisor_info", "/config/info"),
    MyRedirect("supervisor_store", "/config/apps/available"),
    MyRedirect("supervisor_addons", "/config/apps"),
    MyRedirect("supervisor_app", "/config/app", "app"),
    MyRedirect("supervisor_addon", "/config/app", "addon"),
    MyRedirect("supervisor_add_addon_repository", "/config/apps/available", "repository_url"),
    MyRedirect("hacs_repository", "/hacs/_my_redirect/hacs_repository", "owner", "repository", "category"),
    MyRedirect("lights", "/lights"),
)
