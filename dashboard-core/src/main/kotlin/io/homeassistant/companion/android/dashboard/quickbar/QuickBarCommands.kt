package io.homeassistant.companion.android.dashboard.quickbar

import io.homeassistant.companion.android.dashboard.logbook.domainToName

// The quick bar's Commands section, for admins. Port of `generateActionCommands` (frontend@20260624.6
// src/data/quick_bar.ts).

/**
 * Port of `generateActionCommands`: reloading each integration with a `reload` action, the themes, the core
 * configuration and everything, then restarting and stopping the server; ordered by name.
 */
fun QuickBarSource.commandItems(): List<QuickBarItem> =
    (reloadCommands() + serverControlCommands()).sortedBySortingLabel()

/** Port of `generateReloadCommands`. */
private fun QuickBarSource.reloadCommands(): List<QuickBarItem> {
    val secondary = hass.localize("$COMMANDS.types.reload")
    // Until the actions are loaded, only the reloads every server has are offered
    val domains = hass.services.orEmpty().filterValues { RELOAD in it }.keys.map { domain ->
        val primary = hass.localize("$COMMANDS.reload.$domain").ifEmpty {
            hass.localize("$COMMANDS.reload.reload", mapOf("domain" to hass.domainToName(domain)))
        }
        Triple(primary, domain, RELOAD)
    }
    val fixed = listOf(
        Triple(hass.localize("$COMMANDS.reload.themes"), "frontend", "reload_themes"),
        Triple(hass.localize("$COMMANDS.reload.core"), HOMEASSISTANT, "reload_core_config"),
        Triple(hass.localize("$COMMANDS.reload.all"), HOMEASSISTANT, "reload_all"),
    )
    return (domains + fixed).mapIndexed { index, (primary, domain, service) ->
        QuickBarItem(
            id = "command_${index}_$primary",
            section = QuickBarSection.COMMAND,
            primary = primary,
            secondary = secondary,
            icon = domainIcon(domain),
            action = QuickBarAction.CallService(domain, service, confirm = false),
            searchLabels = commandLabels(primary, domain, secondary),
            sortingLabel = "${primary}_${secondary}_$domain",
        )
    }
}

/** Port of `generateServerControlCommands`: restarting and stopping the server, once confirmed. */
private fun QuickBarSource.serverControlCommands(): List<QuickBarItem> {
    val secondary = hass.localize("$COMMANDS.types.home_assistant_control")
    return SERVER_ACTIONS.mapIndexed { index, action ->
        val primary = hass.localize(
            "$COMMANDS.home_assistant_control.perform_action",
            mapOf("action" to hass.localize("$COMMANDS.home_assistant_control.$action")),
        )
        QuickBarItem(
            id = "server_control_${index}_$action",
            section = QuickBarSection.COMMAND,
            primary = primary,
            secondary = secondary,
            icon = domainIcon(HOMEASSISTANT),
            action = QuickBarAction.CallService(HOMEASSISTANT, action, confirm = true),
            searchLabels = commandLabels(primary, HOMEASSISTANT, secondary),
            sortingLabel = "${primary}_${secondary}_$action",
        )
    }
}

private fun commandLabels(primary: String, domain: String, secondary: String) =
    mapOf("primary" to primary, "domain" to domain, "secondary" to secondary)

private const val COMMANDS = "$QUICK_BAR.commands"
private const val RELOAD = "reload"
private const val HOMEASSISTANT = "homeassistant"
private val SERVER_ACTIONS = listOf("restart", "stop")
