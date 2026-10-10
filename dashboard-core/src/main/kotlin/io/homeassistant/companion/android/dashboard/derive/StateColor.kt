package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.color.hsv2rgb
import io.homeassistant.companion.android.dashboard.color.rgb2hex
import io.homeassistant.companion.android.dashboard.color.rgb2hsv
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.text.Normalizer

/**
 * Port of `computeCssColor` (frontend@20260624.6 src/common/color/compute-color.ts): theme colour names become
 * [DisplayColor.Theme], anything else is a literal CSS colour.
 */
fun cssColor(color: String): DisplayColor = if (color in THEME_COLORS ||
    color in YAML_ONLY_THEME_COLORS
) {
    DisplayColor.Theme(color)
} else {
    DisplayColor.Literal(color)
}

/**
 * The colour of [state] (or [stateValue]), or `null` when its domain has no state colours.
 * Ports of `stateColorCss` and `stateColorProperties` (src/common/entity/state_color.ts).
 */
fun stateColor(state: EntityState, stateValue: String? = null): DisplayColor? {
    val value = stateValue ?: state.state
    val deviceClass = state.attributes.string("device_class")
    val battery = if (state.domain == "sensor" && deviceClass == "battery") batteryColorVariable(value) else null
    // A group of entities of one coloured domain takes that domain's colours
    val colorDomain = (if (state.domain == "group") groupDomain(state) else null)
        ?.takeIf { it in STATE_COLORED_DOMAINS } ?: state.domain
    return when {
        value == STATE_UNAVAILABLE -> DisplayColor.State(listOf("state-unavailable-color"))
        battery != null -> DisplayColor.State(listOf(battery))
        colorDomain in STATE_COLORED_DOMAINS ->
            DisplayColor.State(domainColorVariables(colorDomain, deviceClass, value, state.isActive(value)))
        else -> null
    }
}

/**
 * The colour of [state] as if it were of [domain] in [value]: port of `domainStateColorProperties`, which the
 * climate humidity control uses with the humidifier's colours.
 */
fun domainStateColor(domain: String, state: EntityState, value: String): DisplayColor = DisplayColor.State(
    domainColorVariables(domain, state.attributes.string("device_class"), value, state.isActive(value)),
)

/** The one domain of a group's entities, `null` when they are of several. */
private fun groupDomain(state: EntityState): String? = state.attributes.array("entity_id")?.mapNotNull {
    it.stringOrNull?.substringBefore('.')
}?.distinct()?.singleOrNull()

/** Port of `domainColorProperties`. */
private fun domainColorVariables(domain: String, deviceClass: String?, state: String, active: Boolean): List<String> {
    val stateKey = slugify(state)
    val activeKey = if (active) "active" else "inactive"
    return listOfNotNull(
        deviceClass?.ifEmpty { null }?.let { "state-$domain-$it-$stateKey-color" },
        "state-$domain-$stateKey-color",
        "state-$domain-$activeKey-color",
        "state-$activeKey-color",
    )
}

/** Port of `batteryStateColorProperty` (src/common/entity/color/battery_color.ts). */
private fun batteryColorVariable(state: String): String? {
    val value = jsNumber(state)
    return when {
        value.isNaN() -> null
        value >= BATTERY_HIGH -> "state-sensor-battery-high-color"
        value >= BATTERY_MEDIUM -> "state-sensor-battery-medium-color"
        else -> "state-sensor-battery-low-color"
    }
}

/**
 * Port of `slugify` (src/common/string/slugify.ts) for state values: accents removed, everything that is not a
 * letter or digit becomes one `_`, and an empty result is `unknown`.
 */
internal fun slugify(value: String): String {
    if (value.isEmpty()) return ""
    val ascii = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    val slug = ascii.replace(COMMA_BETWEEN_DIGITS, "$1").replace(NON_WORD, "_").trim('_')
    return slug.ifEmpty { "unknown" }
}

private const val BATTERY_HIGH = 70
private const val BATTERY_MEDIUM = 30
private val COMBINING_MARKS = Regex("\\p{M}+")
private val COMMA_BETWEEN_DIGITS = Regex("(\\d),(?=\\d)")
private val NON_WORD = Regex("[^a-z0-9]+")

/** Port of `THEME_COLORS` (src/common/color/compute-color.ts). */
private val THEME_COLORS = setOf(
    "primary", "accent", "red", "pink", "purple", "deep-purple", "indigo", "blue", "light-blue", "cyan", "teal",
    "green", "light-green", "lime", "yellow", "amber", "orange", "deep-orange", "brown", "light-grey", "grey",
    "dark-grey", "blue-grey", "black", "white",
)
private val YAML_ONLY_THEME_COLORS = setOf("primary-text", "secondary-text", "disabled")

/** Port of `STATE_COLORED_DOMAIN` (src/common/entity/state_color.ts). */
private val STATE_COLORED_DOMAINS = setOf(
    "alarm_control_panel", "alert", "automation", "binary_sensor", "calendar", "camera", "climate", "cover",
    "device_tracker", "fan", "group", "humidifier", "input_boolean", "lawn_mower", "light", "lock", "media_player",
    "person", "plant", "remote", "schedule", "script", "siren", "sun", "switch", "timer", "update", "vacuum", "valve",
    "water_heater", "weather",
)

/**
 * A light's own colour from `rgb_color`, made lighter for contrast, or `null` when it has none.
 * Port of the light branch of the entity heading badge's `_computeStateColor` (frontend@20260624.6
 * src/panels/lovelace/heading-badges/hui-entity-heading-badge.ts).
 */
fun lightColor(state: EntityState): DisplayColor? {
    val rgb = state.attributes.array("rgb_color")?.map { jsNumber(it) }
        ?.takeIf { state.domain == "light" && it.size == RGB_SIZE } ?: return null
    val hsv = rgb2hsv(rgb[0], rgb[1], rgb[2])
    if (hsv[1] < MIN_SATURATION) {
        // Very light colours (white) get a fixed value instead of more saturation
        if (hsv[1] < WHITE_SATURATION) hsv[2] = WHITE_VALUE else hsv[1] = MIN_SATURATION
    }
    return DisplayColor.Literal(rgb2hex(hsv2rgb(hsv)))
}

private const val RGB_SIZE = 3

private const val MIN_SATURATION = 0.4
private const val WHITE_SATURATION = 0.1
private const val WHITE_VALUE = 225.0
