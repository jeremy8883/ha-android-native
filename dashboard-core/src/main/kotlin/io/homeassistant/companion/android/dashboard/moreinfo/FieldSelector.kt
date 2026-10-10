package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * How a field is edited: the selectors of `ha-selector` (frontend@20260624.6 src/components/ha-selector/) that the
 * details draw natively.
 */
sealed interface FieldSelector {
    /** `ha-selector-text`: a text field, on several lines when [multiline], hidden for passwords. */
    data class Text(val multiline: Boolean, val password: Boolean, val suffix: String?) : FieldSelector

    /**
     * `ha-selector-number`: a slider with a box, or the box alone ([box]: `mode: box`, or without a minimum and
     * maximum); [step] `null` for any.
     */
    data class Number(val min: Double?, val max: Double?, val step: Double?, val unit: String?, val box: Boolean) :
        FieldSelector

    /** `ha-selector-boolean`: a switch. */
    data object Toggle : FieldSelector

    /** `ha-selector-select`: one of [options] (value and label), or several when [multiple]. */
    data class Select(val options: List<Pair<String, String>>, val multiple: Boolean) : FieldSelector

    /**
     * `ha-selector-entity`: an entity, of [domains] when given (several when [multiple]), picked from [options]
     * (entity ID and name).
     */
    data class Entity(
        val domains: Set<String>,
        val multiple: Boolean,
        val options: List<Pair<String, String>> = emptyList(),
    ) : FieldSelector

    /** `ha-selector-time`: a time, "HH:mm:ss". */
    data object Time : FieldSelector

    /** `ha-selector-date`: a date, "yyyy-MM-dd". */
    data object Date : FieldSelector

    /** `ha-selector-datetime`: a date and time, "yyyy-MM-dd HH:mm:ss". */
    data object DateTime : FieldSelector

    /** `ha-selector-duration`: hours, minutes and seconds (and days when [enableDay]). */
    data class Duration(val enableDay: Boolean) : FieldSelector

    /** `ha-selector-constant`: a fixed [value], shown as [label]. */
    data class Constant(val value: JsonElement, val label: String?) : FieldSelector

    /** A selector the details don't draw natively, [type] by name; edited as text (sent as typed). */
    data class Unsupported(val type: String) : FieldSelector
}

/** The selector of a field's `selector` object (`{type: options}`). */
internal fun fieldSelector(selector: JsonObject): FieldSelector {
    val (type, raw) = selector.entries.firstOrNull()
        ?: return FieldSelector.Text(multiline = false, password = false, suffix = null)
    val options = raw as? JsonObject ?: JsonObject(emptyMap())
    return when (type) {
        "text" -> FieldSelector.Text(
            options.boolean("multiline") == true,
            options.string("type") == "password",
            options.string("suffix"),
        )
        "number" -> numberSelector(options)
        "boolean" -> FieldSelector.Toggle
        "select" -> selectSelector(options)
        "entity" -> FieldSelector.Entity(entityDomains(options), options.boolean("multiple") == true)
        "duration" -> FieldSelector.Duration(options.boolean("enable_day") == true)
        "constant" -> FieldSelector.Constant(options["value"] ?: JsonPrimitive(true), options.string("label"))
        else -> SIMPLE_SELECTORS[type] ?: FieldSelector.Unsupported(type)
    }
}

private fun numberSelector(options: JsonObject): FieldSelector.Number {
    val min = number(options["min"])
    val max = number(options["max"])
    return FieldSelector.Number(
        min = min,
        max = max,
        step = number(options["step"]),
        unit = options.string("unit_of_measurement"),
        box = options.string("mode") == "box" || min == null || max == null,
    )
}

private fun selectSelector(options: JsonObject) = FieldSelector.Select(
    (options["options"] as? JsonArray).orEmpty().mapNotNull { option ->
        when (option) {
            is JsonPrimitive -> option.content to option.content
            is JsonObject -> option.string("value")?.let { it to (option.string("label") ?: it) }
            else -> null
        }
    },
    options.boolean("multiple") == true,
)

/** The selectors without options. */
private val SIMPLE_SELECTORS = mapOf(
    "time" to FieldSelector.Time,
    "date" to FieldSelector.Date,
    "datetime" to FieldSelector.DateTime,
)

/** The domains an entity selector takes: its `filter`s' and its legacy `domain`. */
private fun entityDomains(options: JsonObject): Set<String> {
    val filters = when (val filter = options["filter"]) {
        is JsonArray -> filter.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(filter)
        else -> emptyList()
    } + listOf(options)
    return filters.flatMap { filter ->
        when (val domain = filter["domain"]) {
            is JsonArray -> domain.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(domain.content)
            else -> emptyList()
        }
    }.toSet()
}
