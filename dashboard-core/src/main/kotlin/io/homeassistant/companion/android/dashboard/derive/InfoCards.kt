package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean

/**
 * The repairs card: the active, non-ignored repair issues. Port of `HuiRepairsCard`
 * (frontend@20260624.6 src/panels/lovelace/cards/hui-repairs-card.ts).
 */
fun HassSnapshot.repairsModel(card: CardConfig): InfoTileModel {
    val count = repairsIssues?.size
    return InfoTileModel(
        label = localize("ui.card.repairs.title"),
        icon = "mdi:wrench",
        color = COLOR_WARNING,
        secondary = countText("repairs", "count_issues", "no_issues", count ?: 0),
        loading = count == null,
        vertical = card.json.boolean("vertical") == true,
    )
}

/**
 * The updates card: update entities with an installable update. Port of `HuiUpdatesCard`
 * (src/panels/lovelace/cards/hui-updates-card.ts).
 */
fun HassSnapshot.updatesModel(card: CardConfig): InfoTileModel = InfoTileModel(
    label = localize("ui.card.updates.title"),
    icon = "mdi:package-up",
    color = COLOR_INFO,
    secondary = countText("updates", "count_updates", "no_updates", installableUpdates().size),
    loading = false,
    vertical = card.json.boolean("vertical") == true,
)

/**
 * The discovered devices card: config flows started by discovery. Port of `HuiDiscoveredDevicesCard`
 * (src/panels/lovelace/cards/hui-discovered-devices-card.ts).
 */
fun HassSnapshot.discoveredDevicesModel(card: CardConfig): InfoTileModel {
    val count = discoveredFlows?.size
    return InfoTileModel(
        label = localize("ui.card.discovered-devices.title"),
        icon = "mdi:devices",
        color = COLOR_INFO,
        secondary = countText("discovered-devices", "count_devices", "no_devices", count ?: 0),
        loading = count == null,
        vertical = card.json.boolean("vertical") == true,
    )
}

/**
 * Whether [card] hides itself, as upstream cards do through `card-visibility-changed`: the repairs, updates and
 * discovered devices cards are for admins only, and with `hide_empty` they hide when there is nothing to show.
 * Hidden cards take no space in the layout.
 */
fun HassSnapshot.cardHidesItself(card: CardConfig): Boolean {
    val hideEmpty = card.json.boolean("hide_empty") == true
    val isAdmin = user?.isAdmin == true
    return when (card.type) {
        "repairs" -> !isAdmin || (hideEmpty && repairsIssues?.isEmpty() == true)
        "updates" -> !isAdmin || (hideEmpty && installableUpdates().isEmpty())
        "discovered-devices" -> !isAdmin || (hideEmpty && discoveredFlows?.isEmpty() == true)
        else -> false
    }
}

/** Ports of `filterUpdateEntities` and `updateCanInstall` (src/data/update.ts), without the skipped ones. */
private fun HassSnapshot.installableUpdates() = states.values.filter {
    it.domain == "update" && it.state == "on" && it.supportsFeature(EntityFeature.UPDATE_INSTALL)
}

private fun HassSnapshot.countText(card: String, countKey: String, noneKey: String, count: Int): String =
    if (count > 0) {
        localize("ui.card.$card.$countKey", mapOf("count" to count.toString()))
    } else {
        localize("ui.card.$card.$noneKey")
    }

private const val COLOR_WARNING = "warning"
private const val COLOR_INFO = "info"
