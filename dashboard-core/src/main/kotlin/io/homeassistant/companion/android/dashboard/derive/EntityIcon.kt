package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * The icon to show for [entityId], as `ha-state-icon` picks it (frontend@20260624.6
 * src/components/ha-state-icon.ts): [configIcon], then the registry or `icon` attribute, then the icon
 * translations for its state, then a fixed icon for its domain. `null` only when the entity does not exist.
 *
 * @param stateValue display the icon for this state instead of the current one
 */
fun HassSnapshot.entityIcon(entityId: String, configIcon: String? = null, stateValue: String? = null): String? =
    configIcon?.ifEmpty { null }
        ?: states[entityId]?.let { state -> stateIconOrNull(state, stateValue) ?: fallbackDomainIcon(state.domain) }

/** The icon from the registry, attributes or icon translations, or `null` when the domain fallback applies. */
internal fun HassSnapshot.stateIconOrNull(state: EntityState, stateValue: String? = null): String? =
    registries.entities[state.entityId]?.icon?.ifEmpty { null }
        ?: state.attributes.string("icon")?.ifEmpty { null }
        ?: translatedEntityIcon(state, stateValue)

/** Port of `getEntityIcon` (src/data/icons.ts), for an entity that has state. */
private fun HassSnapshot.translatedEntityIcon(state: EntityState, stateValue: String?): String? {
    val value = stateValue ?: state.state
    return platformIcon(state, value) ?: builtInStateIcon(state, value) ?: componentIcon(state, value)
}

/** The icon the entity's platform translates for its translation key, when the platform is loaded. */
private fun HassSnapshot.platformIcon(state: EntityState, value: String): String? {
    val entry = registries.entities[state.entityId]
    val platform = entry?.platform?.takeIf { it in config.components }
    val translationKey = entry?.translationKey
    if (platform == null || translationKey == null) return null
    return icons.platforms.obj(platform)?.obj(state.domain)?.obj(translationKey)?.let {
        iconFromTranslations(value, it)
    }
}

/** The icon the entity's domain translates for its device class (or by default), when the domain is loaded. */
private fun HassSnapshot.componentIcon(state: EntityState, value: String): String? {
    val componentIcons = icons.entityComponent.obj(state.domain)?.takeIf { state.domain in config.components }
    val deviceClass = state.attributes.string("device_class")?.ifEmpty { null }
    val translations = deviceClass?.let { componentIcons?.obj(it) } ?: componentIcons?.obj(DEFAULT_TRANSLATION)
    return translations?.let { iconFromTranslations(value, it) }
}

/** Port of `getIconFromTranslations`: an exact state icon, else a range icon for numeric states, else the default. */
private fun iconFromTranslations(state: String, translations: JsonObject): String? {
    val exact = if (state.isNotEmpty()) translations.obj("state")?.string(state) else null
    val range = translations.obj("range")
    val number = jsNumber(state)
    val ranged = if (range != null && !number.isNaN()) iconFromRange(number, range) else null
    return exact ?: ranged ?: translations.string("default")
}

/** Port of `getIconFromRange`: the icon of the highest threshold not above [value]. */
private fun iconFromRange(value: Double, range: JsonObject): String? {
    val thresholds = range.keys.map(::jsNumber).filterNot(Double::isNaN).sorted()
    if (thresholds.isEmpty() || value < thresholds.first()) return null
    val selected = thresholds.last { value >= it }
    // Upstream looks the threshold up by its JavaScript string form, so "10.0" keys never match
    return range[jsNumberToString(selected)]?.stringOrNull
}

/** Port of `stateIcon` (src/common/entity/state_icon.ts). */
private fun builtInStateIcon(state: EntityState, value: String): String? = when (state.domain) {
    "update" -> when {
        jsTruthy(state.attributes["in_progress"]) -> "mdi:package-down"
        value == "on" -> "mdi:package-up"
        else -> "mdi:package"
    }
    "device_tracker" -> deviceTrackerIcon(state.attributes.string("source_type"), value)
    "sun" -> if (value == "above_horizon") "mdi:white-balance-sunny" else "mdi:weather-night"
    "input_datetime" -> when {
        !jsTruthy(state.attributes["has_date"]) -> "mdi:clock"
        !jsTruthy(state.attributes["has_time"]) -> "mdi:calendar"
        else -> null
    }
    else -> null
}

/** Port of `deviceTrackerIcon` (src/common/entity/device_tracker_icon.ts). */
private fun deviceTrackerIcon(sourceType: String?, value: String): String = when (sourceType) {
    "router" -> if (value == "home") "mdi:lan-connect" else "mdi:lan-disconnect"
    "bluetooth", "bluetooth_le" -> if (value == "home") "mdi:bluetooth-connect" else "mdi:bluetooth"
    else -> if (value == "not_home") "mdi:account-arrow-right" else "mdi:account"
}

/** JavaScript `String(number)` for the values icon range keys use. */
private fun jsNumberToString(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite() && Math.abs(value) < JS_EXPONENT_THRESHOLD) {
        value.toLong().toString()
    } else {
        value.toString()
    }

/** Port of `FALLBACK_DOMAIN_ICONS` and `DEFAULT_DOMAIN_ICON` (src/data/icons.ts). */
internal fun fallbackDomainIcon(domain: String): String = FALLBACK_DOMAIN_ICONS[domain] ?: DEFAULT_DOMAIN_ICON

private const val DEFAULT_TRANSLATION = "_"
private const val JS_EXPONENT_THRESHOLD = 1e21
private const val DEFAULT_DOMAIN_ICON = "mdi:bookmark"

private val FALLBACK_DOMAIN_ICONS = mapOf(
    "ai_task" to "mdi:star-four-points",
    "air_quality" to "mdi:air-filter",
    "alert" to "mdi:alert",
    "automation" to "mdi:robot",
    "battery" to "mdi:battery",
    "calendar" to "mdi:calendar",
    "climate" to "mdi:thermostat",
    "configurator" to "mdi:cog",
    "conversation" to "mdi:forum-outline",
    "counter" to "mdi:counter",
    "date" to "mdi:calendar",
    "datetime" to "mdi:calendar-clock",
    "demo" to "mdi:home-assistant",
    "device_tracker" to "mdi:account",
    "door" to "mdi:door-open",
    "garage_door" to "mdi:garage-open",
    "gate" to "mdi:gate",
    "google_assistant" to "mdi:google-assistant",
    "group" to "mdi:google-circles-communities",
    "homeassistant" to "mdi:home-assistant",
    "homekit" to "mdi:home-automation",
    "humidity" to "mdi:water-percent",
    "illuminance" to "mdi:brightness-6",
    "image_processing" to "mdi:image-filter-frames",
    "image" to "mdi:image",
    "infrared" to "mdi:led-on",
    "input_boolean" to "mdi:toggle-switch",
    "input_button" to "mdi:button-pointer",
    "input_datetime" to "mdi:calendar-clock",
    "input_number" to "mdi:ray-vertex",
    "input_select" to "mdi:format-list-bulleted",
    "input_text" to "mdi:form-textbox",
    "lawn_mower" to "mdi:robot-mower",
    "light" to "mdi:lightbulb",
    "moisture" to "mdi:water",
    "motion" to "mdi:motion-sensor",
    "notify" to "mdi:comment-alert",
    "number" to "mdi:ray-vertex",
    "occupancy" to "mdi:home-account",
    "persistent_notification" to "mdi:bell",
    "person" to "mdi:account",
    "plant" to "mdi:flower",
    "power" to "mdi:flash",
    "proximity" to "mdi:apple-safari",
    "radio_frequency" to "mdi:radio-tower",
    "remote" to "mdi:remote",
    "scene" to "mdi:palette",
    "schedule" to "mdi:calendar-clock",
    "script" to "mdi:script-text",
    "select" to "mdi:format-list-bulleted",
    "sensor" to "mdi:eye",
    "simple_alarm" to "mdi:bell",
    "siren" to "mdi:bullhorn",
    "stt" to "mdi:microphone-message",
    "sun" to "mdi:white-balance-sunny",
    "temperature" to "mdi:thermometer",
    "text" to "mdi:form-textbox",
    "time" to "mdi:clock",
    "timer" to "mdi:timer-outline",
    "template" to "mdi:code-braces",
    "todo" to "mdi:clipboard-list",
    "tts" to "mdi:speaker-message",
    "vacuum" to "mdi:robot-vacuum",
    "wake_word" to "mdi:chat-sleep",
    "weather" to "mdi:weather-partly-cloudy",
    "window" to "mdi:window-closed",
    "zone" to "mdi:map-marker-radius",
)
