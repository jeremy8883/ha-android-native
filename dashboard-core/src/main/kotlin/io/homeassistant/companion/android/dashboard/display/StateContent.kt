package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.STATE_UNKNOWN
import io.homeassistant.companion.android.dashboard.derive.TIMESTAMP_STATE_DOMAINS
import io.homeassistant.companion.android.dashboard.derive.entityNameDisplay
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsString
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.floor
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
 * @param name the text of the `name` content
 * @param timeFormat a card's `time_format` for timestamps (`relative`, `total`, `date`, `time`, `datetime`)
 */
fun HassSnapshot.stateDisplay(
    state: EntityState,
    content: JsonElement?,
    now: Instant,
    name: String? = null,
    timeFormat: String? = null,
): String {
    val contents = normalizeContent(content) ?: DEFAULT_STATE_CONTENT[state.domain] ?: listOf(CONTENT_STATE)
    val values = contents.mapNotNull { computeContent(state, it, now, name, timeFormat)?.ifEmpty { null } }
    return if (values.isEmpty()) formatEntityState(state) else values.joinToString(SEPARATOR_DOT)
}

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
    name: String?,
    timeFormat: String?,
): String? {
    val domain = state.domain
    if (content == CONTENT_STATE) return stateContent(state, now, timeFormat)
    if (content == "name" && !name.isNullOrEmpty()) return name
    if (content in NAME_CONTENTS) {
        val item = JsonObject(mapOf("type" to JsonPrimitive(content.removeSuffix("_name"))))
        return entityNameDisplay(state, item).ifEmpty { null }
    }

    val relativeMillis: Double? = when {
        content in TIMESTAMP_STATE_PROPS ->
            if (content ==
                "last_changed"
            ) {
                state.lastChanged * MILLIS_PER_SECOND
            } else {
                state.lastUpdated * MILLIS_PER_SECOND
            }
        domain == "input_datetime" && content == "timestamp" ->
            (jsNumber(state.attributes["timestamp"]) * MILLIS_PER_SECOND).takeUnless { it.isNaN() }
        content in TIMESTAMP_CONTENTS || content in TIMESTAMP_DOMAIN_CONTENTS[domain].orEmpty() ->
            timestampAttributeMillis(state.attributes[content])
        else -> null
    }
    // Upstream shows any truthy value or 0; an unparsable date shows as invalid
    if (relativeMillis != null) {
        return timestamp(relativeMillis, now, timeFormat)
    }

    if (content in SPECIAL_CONTENT[domain].orEmpty()) {
        return if (content == "install_status") updateStateDisplay(state) else timerDisplay(state, now)
    }
    val attribute = state.attributes[content]
    if (attribute == null || attribute is JsonNull) return null
    if (content in HIDDEN_ZERO_ATTRIBUTES[domain].orEmpty() && !jsTruthy(attribute)) return null
    return formatEntityAttributeValue(state, content)
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

/** Port of `computeUpdateStateDisplay` (src/data/update.ts). */
private fun HassSnapshot.updateStateDisplay(state: EntityState): String {
    val attributes = state.attributes
    if (jsTruthy(attributes["in_progress"])) {
        val percentage = attributes["update_percentage"]
        if (state.supportsFeature(EntityFeature.UPDATE_PROGRESS) && percentage !is JsonNull) {
            // Upstream passes `display_precision` even when it is undefined, which leaves Intl's default of 3
            val precision = attributes["display_precision"]?.stringOrNull?.toIntOrNull()
            val number = jsNumber(percentage)
            val progress = if (number.isNaN() || number.isInfinite()) {
                number.toString()
            } else {
                formats.number(BigDecimal(number), precision ?: 0, precision ?: INTL_DEFAULT_MAX_FRACTION_DIGITS)
            }
            return localize("ui.card.update.installing_with_progress", mapOf("progress" to progress))
        }
        return localize("ui.card.update.installing")
    }
    val latest = attributes.string("latest_version")
    if (state.state == "off" && !latest.isNullOrEmpty() && attributes.string("skipped_version") == latest) return latest
    return formatEntityState(state)
}

/** Port of `ha-timer-remaining-time` with `computeDisplayTimer` (src/data/timer.ts). */
private fun HassSnapshot.timerDisplay(state: EntityState, now: Instant): String {
    val remaining = timerTimeRemaining(state, now)
    if (state.state == "idle" || remaining == 0.0) return formatEntityState(state)
    val display = secondsToDuration(remaining ?: 0.0) ?: "0"
    return if (state.state == "paused") "$display (${formatEntityState(state)})" else display
}

private fun timerTimeRemaining(state: EntityState, now: Instant): Double? {
    val remaining = state.attributes.string("remaining")?.ifEmpty { null } ?: return null
    if (state.state == "active") {
        val finishes = state.attributes.string("finishes_at")?.let { parseJsDate(it, java.time.ZoneOffset.UTC) }
            ?: return Double.NaN
        return maxOf((finishes.toEpochMilli() - now.toEpochMilli()) / MILLIS_PER_SECOND, 0.0)
    }
    val parts = remaining.split(":").map { jsNumber(it) }
    return parts.getOrElse(0) { Double.NaN } * SECS_PER_HOUR + parts.getOrElse(1) { Double.NaN } * SECS_PER_MIN +
        parts.getOrElse(2) { Double.NaN }
}

/** Port of `secondsToDuration` (src/common/datetime/seconds_to_duration.ts). */
private fun secondsToDuration(seconds: Double): String? {
    if (seconds.isNaN()) return null
    val h = floor(seconds / SECS_PER_HOUR).toLong()
    val m = floor((seconds % SECS_PER_HOUR) / SECS_PER_MIN).toLong()
    val s = floor((seconds % SECS_PER_HOUR) % SECS_PER_MIN).toLong()
    return when {
        h > 0 -> "$h:${m.pad()}:${s.pad()}"
        m > 0 -> "$m:${s.pad()}"
        s > 0 -> "$s"
        else -> null
    }
}

private fun Long.pad(): String = toString().padStart(2, '0')

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }

private const val CONTENT_STATE = "state"
private const val INTL_DEFAULT_MAX_FRACTION_DIGITS = 3
private const val SEPARATOR_DOT = " · "
private const val DEVICE_CLASS_UPTIME = "uptime"
private const val MILLIS_PER_SECOND = 1000.0
private const val SECS_PER_HOUR = 3600
private const val SECS_PER_MIN = 60

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
