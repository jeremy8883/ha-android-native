package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.parsePanels
import kotlinx.serialization.json.JsonObject

/**
 * @property panels the url paths of the registered panels
 * @property panelInfo the panels with their titles, icons and visibility, for the navigation sidebar
 */
data class ServerInfo(
    val user: HassUser,
    val config: HassConfig,
    val panels: Set<String>,
    val panelInfo: Map<String, PanelInfo>,
)

internal fun parseServerInfo(bundle: JsonObject): Fetched<ServerInfo> {
    val user = (bundle[USER] as? JsonObject)?.let(::parseUser)
    val config = (bundle[CONFIG] as? JsonObject)?.let(::parseConfig)
    val panels = bundle[PANELS] as? JsonObject
    if (user == null || config == null || panels == null) return unexpected("server info")
    return Fetched.Success(ServerInfo(user, config, panels.keys, parsePanels(panels)))
}

private fun parseUser(user: JsonObject): HassUser? = user.string("id")?.let { id ->
    HassUser(
        id = id,
        name = user.string("name"),
        isAdmin = user.boolean("is_admin") == true,
        isOwner = user.boolean("is_owner") == true,
    )
}

private fun parseConfig(config: JsonObject): HassConfig? = config.array("components")?.let { components ->
    HassConfig(
        state = config.string("state"),
        recoveryMode = config.boolean("recovery_mode") == true,
        version = config.string("version"),
        components = components.mapNotNull { it.stringOrNull }.toSet(),
        // Measures without a unit are left out
        unitSystem = config.obj("unit_system")
            ?.mapNotNull { (measure, unit) -> unit.stringOrNull?.let { measure to it } }
            .orEmpty().toMap(),
        locationName = config.string("location_name"),
        currency = config.string("currency"),
    )
}
