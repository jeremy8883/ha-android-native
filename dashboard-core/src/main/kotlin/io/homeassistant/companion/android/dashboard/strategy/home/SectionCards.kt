package io.homeassistant.companion.android.dashboard.strategy.home

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Card and section configs the home strategies build, as upstream writes them inline

internal fun gridSection(cards: List<JsonObject>): JsonObject = buildJsonObject {
    put("type", "grid")
    put("cards", JsonArray(cards))
}

internal fun heading(text: String, icon: String, tapAction: JsonObject?): JsonObject = buildJsonObject {
    put("type", "heading")
    put("heading", text)
    put("icon", icon)
    tapAction?.let { putTapAction(it) }
}

internal fun navigate(path: String): JsonObject = buildJsonObject {
    put("action", "navigate")
    put("navigation_path", path)
}

internal fun JsonObjectBuilder.putTapAction(action: JsonObject) {
    put("tap_action", action)
}
