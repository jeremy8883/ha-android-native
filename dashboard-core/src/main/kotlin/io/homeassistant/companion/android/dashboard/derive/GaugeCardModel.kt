package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A gauge card: a half circle filled to the [value] between [min] and [max] in its severity's [color] (or a needle
 * over coloured [levels]), its value as text, and the name under it. [warning] replaces it all when the entity is
 * missing, unavailable or not a number. Port of `hui-gauge-card` with `ha-gauge` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-gauge-card.ts, src/components/ha-gauge.ts).
 */
data class GaugeCardModel(
    val name: String,
    val value: Double,
    val min: Double,
    val max: Double,
    val valueText: String,
    val color: DisplayColor?,
    val needle: Boolean,
    val levels: List<GaugeLevel>,
    val actions: ElementActions,
    val warning: String?,
)

/** A coloured stretch of the gauge from [level], with the [label] shown instead of the value while in it. */
data class GaugeLevel(val level: Double, val color: DisplayColor, val label: String?)

/** The gauge card of [card]. */
fun HassSnapshot.gaugeCardModel(card: CardConfig): GaugeCardModel {
    val json = card.json
    val entityId = json.string("entity").orEmpty()
    val state = states[entityId]
    val attribute = json.string("attribute")
    val raw = if (attribute != null) state?.attributes?.get(attribute) else state?.let { JsonPrimitive(it.state) }
    val value = (raw as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()
    val levels = gaugeLevels(json)
    val needle = json.boolean("needle") == true
    val number = value ?: 0.0
    return GaugeCardModel(
        name = state?.let { entityNameDisplay(it, json["name"]) }.orEmpty(),
        value = number,
        min = number(json["min"]) ?: DEFAULT_MIN,
        max = number(json["max"]) ?: DEFAULT_MAX,
        valueText = gaugeValueText(state, attribute, json.string("unit"), levels.takeIf { needle }, number),
        color = if (needle) null else severity(json, number),
        needle = needle,
        levels = if (needle) levels else emptyList(),
        actions = elementActions(json, tapWhenUnset = true),
        warning = gaugeWarning(entityId, state, attribute, value),
    )
}

/** Why the gauge can't show: the entity is missing, unavailable or not a number; `null` when it can. */
private fun HassSnapshot.gaugeWarning(
    entityId: String,
    state: EntityState?,
    attribute: String?,
    value: Double?,
): String? = when {
    state == null -> localize("ui.card.common.entity_not_found")
    state.state == STATE_UNAVAILABLE -> localize("$WARNING.entity_unavailable", mapOf("entity" to entityId))
    value == null -> localize(
        if (attribute != null) "$WARNING.attribute_not_numeric" else "$WARNING.entity_non_numeric",
        mapOf("entity" to entityId, "attribute" to attribute.orEmpty()),
    )
    else -> null
}

/** The value as text: a needle's segment label, else the formatted value with the custom [unit] or its own. */
private fun HassSnapshot.gaugeValueText(
    state: EntityState?,
    attribute: String?,
    unit: String?,
    needleLevels: List<GaugeLevel>?,
    value: Double,
): String {
    val label = needleLevels?.sortedBy { it.level }?.lastOrNull { value >= it.level }?.label
    val formatted = state?.let {
        if (attribute !=
            null
        ) {
            formatEntityAttributeValue(it, attribute)
        } else {
            formatEntityState(it)
        }
    }.orEmpty()
    return label ?: unit?.let { "${withoutUnit(formatted, state?.attributes?.string("unit_of_measurement"))} $it" }
        ?: formatted
}

/** The value without the entity's own unit, as `valueFromParts` gives it. */
private fun withoutUnit(text: String, unit: String?): String = unit?.let { text.removeSuffix(it).trim() } ?: text

/** Port of `_computeSeverity`: the colour of the segment the value is in, else the old severities', else blue. */
private fun severity(json: JsonObject, value: Double): DisplayColor =
    segments(json)?.let { segmentColor(it, value) } ?: severityColor(json, value)

private fun segmentColor(segments: List<GaugeLevel>, value: Double): DisplayColor {
    val sorted = segments.sortedBy { it.level }
    return sorted.indices.firstOrNull { i ->
        value >= sorted[i].level &&
            (i + 1 == sorted.size || value < sorted[i + 1].level)
    }
        ?.let { sorted[it].color } ?: NORMAL
}

/** The old `severity` option: the first three severities by level (upstream compares only those). */
private fun severityColor(json: JsonObject, value: Double): DisplayColor {
    val sections = (json["severity"] as? JsonObject)?.map { (name, level) -> name to number(level) }.orEmpty()
    val valid = sections.mapNotNull { (name, level) -> level?.takeIf { name in SEVERITIES }?.let { name to it } }
    val sorted = valid.sortedBy { it.second }.take(SEVERITY_SECTIONS)
    val index = sorted.indices.lastOrNull { value >= sorted[it].second }
    return index?.takeIf {
        valid.size == sections.size && sections.isNotEmpty()
    }?.let { SEVERITIES.getValue(sorted[it].first) }
        ?: NORMAL
}

/** Port of `_severityLevels`: the segments, else the old severities, else blue from 0. */
private fun gaugeLevels(json: JsonObject): List<GaugeLevel> = segments(json)
    ?: (json["severity"] as? JsonObject)?.mapNotNull { (name, level) ->
        val color = SEVERITIES[name]
        number(level)?.let { at -> color?.let { GaugeLevel(at, it, null) } }
    }
    ?: listOf(GaugeLevel(0.0, NORMAL, null))

private fun segments(json: JsonObject): List<GaugeLevel>? = (json["segments"] as? JsonArray)?.mapNotNull { segment ->
    (segment as? JsonObject)?.let { s ->
        // Upstream strokes with the colour as it's written ("green" is CSS's, not the theme's)
        number(s["from"])?.let { from ->
            GaugeLevel(from, DisplayColor.Literal(s.string("color").orEmpty()), s.string("label"))
        }
    }
}

private const val WARNING = "ui.panel.lovelace.warning"
private const val SEVERITY_SECTIONS = 3
private const val DEFAULT_MIN = 0.0
private const val DEFAULT_MAX = 100.0

/** `severityMap`. */
private val NORMAL: DisplayColor = DisplayColor.Theme("info")
private val SEVERITIES: Map<String, DisplayColor> = mapOf(
    "red" to DisplayColor.Theme("error"),
    "green" to DisplayColor.Theme("success"),
    "yellow" to DisplayColor.Theme("warning"),
    "normal" to NORMAL,
)
