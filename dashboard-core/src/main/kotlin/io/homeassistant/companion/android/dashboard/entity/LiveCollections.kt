package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The issues of a `repairs/list_issues` result that the repairs card counts: active and not ignored.
 * Port of the filter in `HuiRepairsCard.hassSubscribe` (frontend@20260624.6 src/panels/lovelace/cards/hui-repairs-card.ts).
 */
fun activeRepairsIssues(result: JsonObject): List<JsonObject> =
    result.objects("issues").filter { it.boolean("active") != false && it.boolean("ignored") != true }

/**
 * Apply one `config_entries/flow/subscribe` event (a list of `{type, flow_id, flow}` messages) to the discovered
 * flows: `null`-type messages are a full snapshot, `added` adds, `removed` removes. Only flows from discovery
 * sources are kept. Port of the handler in `HuiDiscoveredDevicesCard.hassSubscribe`
 * (src/panels/lovelace/cards/hui-discovered-devices-card.ts). `null` when [event] is not a list of messages.
 */
fun applyConfigFlowMessages(current: List<JsonObject>?, event: JsonElement): List<JsonObject>? {
    val messages = (event as? JsonArray)?.filterIsInstance<JsonObject>() ?: return null
    // As upstream, no messages means no flows
    if (messages.isEmpty()) return emptyList()
    var flows = current
    var fullUpdate = false
    val added = mutableListOf<JsonObject>()
    for (message in messages) {
        val type = message["type"]?.takeUnless { it is JsonNull }?.let { message.string("type") }
        when (type) {
            "removed" -> flows = flows.orEmpty().filter { it.string("flow_id") != message.string("flow_id") }
            null, "added" -> {
                if (type == null) fullUpdate = true
                val flow = message.obj("flow")
                if (flow != null && flow.obj("context")?.string("source") in DISCOVERY_SOURCES) added += flow
            }
        }
    }
    if (added.isEmpty() && !fullUpdate) return flows.orEmpty()
    return (if (fullUpdate) emptyList() else flows.orEmpty()) + added
}

/** Port of `DISCOVERY_SOURCES` (src/data/config_flow.ts). */
private val DISCOVERY_SOURCES = setOf(
    "bluetooth", "dhcp", "discovery", "esphome", "hardware", "hassio", "homekit", "integration_discovery", "mqtt",
    "ssdp", "unignore", "usb", "zeroconf",
)
