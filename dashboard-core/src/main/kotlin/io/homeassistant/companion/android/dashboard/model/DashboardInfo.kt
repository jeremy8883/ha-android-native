package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A dashboard the user can open. [urlPath] `null` is the default dashboard.
 *
 * Parsed from `lovelace/dashboards/list` (core: homeassistant/components/lovelace/websocket.py).
 */
data class DashboardInfo(val urlPath: String?, val title: String?, val mode: String?, val requireAdmin: Boolean)

/** Parse a `lovelace/dashboards/list` result, skipping malformed entries. */
fun parseDashboards(result: JsonArray): List<DashboardInfo> = result.filterIsInstance<JsonObject>().mapNotNull {
    DashboardInfo(
        urlPath = it.string("url_path") ?: return@mapNotNull null,
        title = it.string("title"),
        mode = it.string("mode"),
        requireAdmin = it.boolean("require_admin") == true,
    )
}

/** Error `code` returned by `lovelace/config` when a dashboard has no stored config. */
const val ERROR_CONFIG_NOT_FOUND = "config_not_found"
