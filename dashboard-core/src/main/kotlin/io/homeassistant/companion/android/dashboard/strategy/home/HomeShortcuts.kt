package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Port of `DEFAULT_SUMMARY_KEYS` (frontend@20260624.6 src/data/home_shortcuts.ts). */
val DEFAULT_SUMMARY_KEYS = listOf("light", "climate", "security", "media_players", "maintenance", "weather", "energy")

/**
 * The saved shortcut items with unknown or duplicate summary keys dropped, followed by any built-in summaries
 * missing from the saved list, in default order. Port of `resolveShortcutItems`.
 */
fun resolveShortcutItems(saved: JsonArray?): List<JsonObject> {
    val result = mutableListOf<JsonObject>()
    val seenSummaryKeys = mutableSetOf<String>()
    for (item in saved.orEmpty().filterIsInstance<JsonObject>()) {
        if (item.string("type") == "summary") {
            val key = item.string("key")
            if (key == null || key !in DEFAULT_SUMMARY_KEYS || !seenSummaryKeys.add(key)) continue
        }
        result += item
    }
    DEFAULT_SUMMARY_KEYS.filter { it !in seenSummaryKeys }.forEach { key ->
        result += buildJsonObject {
            put("type", "summary")
            put("key", key)
        }
    }
    return result
}
