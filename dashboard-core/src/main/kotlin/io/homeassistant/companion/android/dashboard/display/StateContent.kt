package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.STATE_UNKNOWN
import io.homeassistant.companion.android.dashboard.derive.TIMESTAMP_STATE_DOMAINS
import io.homeassistant.companion.android.dashboard.derive.entityNameDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsString
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The text a `state-display` element shows for [state]: the parts of [content] (`state`, an attribute name,
 * `last_changed`, `area_name`, ...) joined with " · ", or the domain's default content (for example a light's
 * brightness, a climate's current temperature). Falls back to the state when no part has a value.
 *
 * Port of `StateDisplay.render` (frontend@20260624.6 src/state-display/state-display.ts).
 *
 * @param content a card's `state_content`: one string or a list, `null` for the domain default
 * @param now the time relative timestamps ("in 10 hours") are computed from
 * @param options the card's options for the content
 */
fun HassSnapshot.stateDisplay(
    state: EntityState,
    content: JsonElement?,
    now: Instant,
    options: StateDisplayOptions = StateDisplayOptions(),
): String {
    val contents = normalizeContent(content) ?: DEFAULT_STATE_CONTENT[state.domain] ?: listOf(CONTENT_STATE)
    val values = contents.mapNotNull { computeContent(state, it, now, options)?.ifEmpty { null } }
    return if (values.isEmpty()) formatEntityState(state) else values.joinToString(SEPARATOR_DOT)
}

/**
 * The options of a `state-display`.
 *
 * @property name the text of the `name` content
 * @property timeFormat a card's `time_format` for timestamps (`relative`, `total`, `date`, `time`, `datetime`)
 * @property dashUnavailable show "—" instead of unavailable and unknown states (`dash-unavailable`)
 */
data class StateDisplayOptions(
    val name: String? = null,
    val timeFormat: String? = null,
    val dashUnavailable: Boolean = false,
)

private fun normalizeContent(content: JsonElement?): List<String>? {
    if (content == null || content is JsonNull) return null
    val items = (content as? JsonArray)?.toList() ?: listOf(content)
    return items.map {
        when (val value = it.jsString()) {
            "last-updated" -> "last_updated"
            "last-changed" -> "last_changed"
            else -> value
        }
    }
}

private fun HassSnapshot.computeContent(
    state: EntityState,
    content: String,
    now: Instant,
    options: StateDisplayOptions,
): String? = when {
    content == CONTENT_STATE -> {
        val noValue = state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN
        if (options.dashUnavailable && noValue) DASH else stateContent(state, now, options.timeFormat)
    }
    content == "name" && !options.name.isNullOrEmpty() -> options.name
    content in NAME_CONTENTS ->
        entityNameDisplay(state, JsonObject(mapOf("type" to JsonPrimitive(content.removeSuffix("_name")))))
            .ifEmpty { null }
    // Upstream shows any truthy value or 0; an unparsable date shows as invalid
    else -> contentMillis(state, content)?.let { timestamp(it, now, options.timeFormat) }
        ?: attributeContent(state, content, now)
}

/** The time [content] stands for, in epoch milliseconds, when it is a timestamp. */
private fun HassSnapshot.contentMillis(state: EntityState, content: String): Double? = when {
    content == "last_changed" -> state.lastChanged * MILLIS_PER_SECOND
    content == "last_updated" -> state.lastUpdated * MILLIS_PER_SECOND
    state.domain == "input_datetime" && content == "timestamp" ->
        (jsNumber(state.attributes["timestamp"]) * MILLIS_PER_SECOND).takeUnless { it.isNaN() }
    content in TIMESTAMP_CONTENTS || content in TIMESTAMP_DOMAIN_CONTENTS[state.domain].orEmpty() ->
        timestampAttributeMillis(state.attributes[content])
    else -> null
}

/** The attribute [content], or the domain's special content (a timer's remaining time, an update's status). */
private fun HassSnapshot.attributeContent(state: EntityState, content: String, now: Instant): String? {
    val attribute = state.attributes[content]
    return when {
        content in SPECIAL_CONTENT[state.domain].orEmpty() ->
            if (content == "install_status") updateStateDisplay(state) else timerDisplay(state, now)
        attribute == null || attribute is JsonNull -> null
        content in HIDDEN_ZERO_ATTRIBUTES[state.domain].orEmpty() && !jsTruthy(attribute) -> null
        else -> formatEntityAttributeValue(state, content)
    }
}

private fun HassSnapshot.stateContent(state: EntityState, now: Instant, timeFormat: String?): String {
    val noValue = state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN
    val deviceClass = state.attributes.string("device_class")
    if ((deviceClass in SENSOR_TIMESTAMP_DEVICE_CLASSES || state.domain in TIMESTAMP_STATE_DOMAINS) && !noValue) {
        val millis = parseJsDate(state.state, formats.zone)?.toEpochMilli()?.toDouble() ?: Double.NaN
        return timestamp(millis, now, timeFormat ?: if (deviceClass == DEVICE_CLASS_UPTIME) "total" else "relative")
    }
    return formatEntityState(state)
}

/**
 * The value of a timestamp attribute as `new Date(value)` takes it, a date string or epoch milliseconds: `null`
 * when upstream skips it (missing, empty, false), NaN when it is not a valid date.
 */
private fun HassSnapshot.timestampAttributeMillis(value: JsonElement?): Double? = when {
    value == null || value is JsonNull -> null
    value is JsonPrimitive && value.isString -> value.content.takeIf { it.isNotEmpty() }?.let {
        parseJsDate(it, formats.zone)?.toEpochMilli()?.toDouble() ?: Double.NaN
    }
    value is JsonPrimitive -> jsNumber(value).takeUnless { it.isNaN() || (it == 0.0 && !value.content.startsWith("0")) }
    else -> Double.NaN
}

/**
 * What `hui-timestamp-display` shows for the time [millis]: a relative time ("In 10 hours", capitalised), the
 * time since it ("5 days"), or the date and/or time.
 */
private fun HassSnapshot.timestamp(millis: Double, now: Instant, format: String?): String {
    if (millis.isNaN()) return localize("ui.panel.lovelace.components.timestamp-display.invalid")
    val instant = Instant.ofEpochMilli(millis.toLong())
    return when (format ?: "relative") {
        "relative" -> formats.relativeTime(instant, now).capitalizeFirst()
        "total" -> formats.relativeTime(now, instant, includeTense = false).capitalizeFirst()
        "date" -> formats.date(instant)
        "datetime" -> formats.dateTime(instant)
        "time" -> formats.time(instant)
        else -> localize("ui.panel.lovelace.components.timestamp-display.invalid_format")
    }
}

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }

private const val CONTENT_STATE = "state"
private const val DASH = "—"
private const val SEPARATOR_DOT = " · "
private const val DEVICE_CLASS_UPTIME = "uptime"
internal const val MILLIS_PER_SECOND = 1000.0

private val NAME_CONTENTS = setOf("device_name", "area_name", "floor_name")
private val TIMESTAMP_STATE_PROPS = setOf("last_updated", "last_changed")
private val TIMESTAMP_CONTENTS = TIMESTAMP_STATE_PROPS + "last_triggered"
private val TIMESTAMP_DOMAIN_CONTENTS = mapOf(
    "calendar" to setOf("start_time", "end_time"),
    "input_datetime" to setOf("timestamp"),
    "sun" to setOf("next_dawn", "next_dusk", "next_midnight", "next_noon", "next_rising", "next_setting"),
)

/** Port of `STATE_DISPLAY_SPECIAL_CONTENT_DOMAINS`. */
private val SPECIAL_CONTENT = mapOf("timer" to setOf("remaining_time"), "update" to setOf("install_status"))

/** Port of `HIDDEN_ZERO_ATTRIBUTES_DOMAINS`. */
private val HIDDEN_ZERO_ATTRIBUTES = mapOf(
    "valve" to setOf("current_position"),
    "cover" to setOf("current_position"),
    "fan" to setOf("percentage"),
    "light" to setOf("brightness"),
)

/** Port of `DEFAULT_STATE_CONTENT_DOMAINS`. */
private val DEFAULT_STATE_CONTENT = mapOf(
    "climate" to listOf("state", "current_temperature"),
    "cover" to listOf("state", "current_position"),
    "fan" to listOf("percentage"),
    "humidifier" to listOf("state", "current_humidity"),
    "light" to listOf("brightness"),
    "timer" to listOf("remaining_time"),
    "update" to listOf("install_status"),
    "valve" to listOf("state", "current_position"),
)
