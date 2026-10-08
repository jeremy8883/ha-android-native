package io.homeassistant.companion.android.dashboard.condition

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.has
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Runtime information conditions depend on besides the entity states.
 *
 * @property maxColumns columns of the enclosing sections view ([sectionsViewColumns]); `null` disables
 *   `view_columns` checks, as upstream does before the view has measured itself
 * @property entityId the card's entity, used by state conditions without their own `entity`
 * @property screen the window size, for `screen` media queries
 * @property now the current time in the zone `time` conditions use (upstream: the user's or the server's zone)
 */
data class ConditionContext(
    val maxColumns: Int? = null,
    val entityId: String? = null,
    val screen: ScreenInfo? = null,
    val now: ZonedDateTime? = null,
)

/** Window size in CSS pixels (dp on Android). */
data class ScreenInfo(val widthDp: Int, val heightDp: Int)

/**
 * Whether all [conditions] are met. Conditions without a `condition` key are legacy state conditions.
 * Port of `checkConditionsMet` (frontend@20260624.6 src/panels/lovelace/common/validate-condition.ts).
 */
fun HassSnapshot.conditionsMet(conditions: List<JsonObject>, context: ConditionContext): Boolean =
    conditions.all { conditionMet(it, context) }

private fun HassSnapshot.conditionMet(condition: JsonObject, context: ConditionContext): Boolean {
    if (!condition.has("condition")) return stateCondition(condition, context)
    val nested = { condition.objects("conditions") }
    return when (condition.string("condition")) {
        "view_columns" -> viewColumnsCondition(condition, context)
        "time" -> timeCondition(condition, context.now)
        "screen" -> condition.string("media_query")?.let { query ->
            context.screen?.let { matchesMediaQuery(query, it) }
        } ==
            true
        "user" -> user?.id?.let { id -> condition.array("users")?.any { it.stringOrNull == id } } == true
        "location" -> locationCondition(condition)
        "numeric_state" -> numericStateCondition(condition, context)
        "and" -> !condition.has("conditions") || conditionsMet(nested(), context)
        "not" -> !condition.has("conditions") || !conditionsMet(nested(), context)
        "or" -> !condition.has("conditions") || nested().any { conditionsMet(listOf(it), context) }
        else -> stateCondition(condition, context)
    }
}

/** Port of `checkStateCondition`. Values naming an existing entity also match that entity's state. */
private fun HassSnapshot.stateCondition(condition: JsonObject, context: ConditionContext): Boolean {
    val entityId = condition.string("entity")?.ifEmpty { null } ?: context.entityId
    val stateObj = entityId?.let(states::get)
    val attribute = condition.string("attribute")?.ifEmpty { null }
    val state = when {
        stateObj == null -> STATE_UNKNOWN
        attribute != null -> stateObj.attributes[attribute]?.takeUnless { it is JsonNull }?.jsString() ?: STATE_UNKNOWN
        else -> stateObj.state
    }
    val isState = condition["state"]?.takeUnless { it is JsonNull } != null
    val value = (if (isState) condition["state"] else condition["state_not"])?.takeUnless { it is JsonNull }
        ?: return false
    val candidates = when (value) {
        is JsonArray -> value.mapNotNull { it.stringOrNull }.let { it + it.mapNotNull(::stateOfEntityId) }
        else -> listOfNotNull(value.stringOrNull).let { it + it.mapNotNull(::stateOfEntityId) }
    }
    return if (isState) state in candidates else state !in candidates
}

/** Port of `checkStateNumericCondition`, with JavaScript `Number()` coercion. */
private fun HassSnapshot.numericStateCondition(condition: JsonObject, context: ConditionContext): Boolean {
    val entityId = condition.string("entity")?.ifEmpty { null } ?: context.entityId
    val stateObj = entityId?.let(states::get)
    val attribute = condition.string("attribute")?.ifEmpty { null }
    val state: JsonElement? = if (attribute !=
        null
    ) {
        stateObj?.attributes?.get(attribute)
    } else {
        stateObj?.state?.let(::JsonPrimitive)
    }

    fun bound(key: String): Pair<Boolean, Double> {
        val raw = condition[key]?.takeUnless { it is JsonNull } ?: return false to Double.NaN
        val resolved = raw.stringOrNull?.let { stateOfEntityId(it) ?: it }?.let(::JsonPrimitive) ?: raw
        return true to jsNumber(resolved)
    }
    val numericState = jsNumber(state)
    if (numericState.isNaN()) return false
    val (hasAbove, above) = bound("above")
    val (hasBelow, below) = bound("below")
    return (!hasAbove || above.isNaN() || above < numericState) && (!hasBelow || below.isNaN() || below > numericState)
}

/** Port of `checkViewColumnsCondition`. */
private fun viewColumnsCondition(condition: JsonObject, context: ConditionContext): Boolean {
    val columns = context.maxColumns?.takeIf { it != 0 } ?: return true
    val min = (condition["min"] as? JsonPrimitive)?.doubleOrNull
    val max = (condition["max"] as? JsonPrimitive)?.doubleOrNull
    return (min == null || columns >= min) && (max == null || columns <= max)
}

/** Port of `checkLocationCondition`: the state of the current user's person entity. */
private fun HassSnapshot.locationCondition(condition: JsonObject): Boolean {
    val userId = user?.id ?: return false
    val person =
        states.values.firstOrNull { it.domain == "person" && it.attributes.string("user_id") == userId } ?: return false
    return condition.array("locations")?.any { it.stringOrNull == person.state } == true
}

/** Port of `checkTimeInRange` (src/common/datetime/check_time.ts). Without a clock the condition is not met. */
private fun timeCondition(condition: JsonObject, now: ZonedDateTime?): Boolean {
    now ?: return false
    val weekdays = condition.array("weekdays")?.mapNotNull { it.stringOrNull }.orEmpty()
    if (weekdays.isNotEmpty()) {
        val today = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
        if (today !in weekdays) return false
    }
    val after = condition.string("after")?.ifEmpty { null }?.let(::parseTime)
    val before = condition.string("before")?.ifEmpty { null }?.let(::parseTime)
    val time = now.toLocalTime()
    return when {
        after != null && before != null && before < after -> time >= after || time <= before // crosses midnight
        after != null && before != null -> time in after..before
        after != null -> time >= after
        before != null -> time <= before
        else -> true
    }
}

private fun parseTime(value: String): LocalTime? {
    val parts = value.split(':').map { it.trim().toIntOrNull() }
    val hours = parts.getOrNull(0) ?: return null
    val minutes = parts.getOrNull(1) ?: return null
    val seconds = if (parts.size == 3) parts[2] ?: return null else 0
    return runCatching { LocalTime.of(hours, minutes, seconds) }.getOrNull()
}

/** Port of `getValueFromEntityId`: the state of [value] when it is the id of an existing entity. */
private fun HassSnapshot.stateOfEntityId(value: String): String? =
    value.takeIf { VALID_ENTITY_ID.matches(it) }?.let { states[it]?.state }

/** JavaScript `String(value)` for JSON values. */
private fun JsonElement.jsString(): String = when (this) {
    is JsonPrimitive -> content
    else -> toString()
}

/** JavaScript `Number(value)`: missing is NaN, null and "" are 0, booleans are 0/1, other strings parse or are NaN. */
internal fun jsNumber(value: JsonElement?): Double = when {
    value == null -> Double.NaN
    value is JsonNull -> 0.0
    value !is JsonPrimitive -> Double.NaN
    !value.isString && value.booleanOrNull != null -> if (value.booleanOrNull == true) 1.0 else 0.0
    !value.isString -> value.doubleOrNull ?: Double.NaN
    value.content.isBlank() -> 0.0
    else -> parseJsNumber(value.content.trim())
}

/** Decimal and exponent literals as `Number("...")` accepts them (hex and binary literals are not used in states). */
private fun parseJsNumber(text: String): Double = when {
    JS_DECIMAL.matches(text) -> text.toDouble()
    text == "Infinity" || text == "+Infinity" -> Double.POSITIVE_INFINITY
    text == "-Infinity" -> Double.NEGATIVE_INFINITY
    else -> Double.NaN
}

private val JS_DECIMAL = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")

private val VALID_ENTITY_ID = Regex("""^(\w+)\.(\w+)$""")
private const val STATE_UNKNOWN = "unknown"
