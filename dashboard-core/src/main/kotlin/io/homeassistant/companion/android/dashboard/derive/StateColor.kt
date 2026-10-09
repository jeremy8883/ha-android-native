package io.homeassistant.companion.android.dashboard.derive

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

/** Port of `rgb2hsv` (src/common/color/convert-color.ts). */
private fun rgb2hsv(r: Double, g: Double, b: Double): DoubleArray {
    val v = maxOf(r, g, b)
    val c = v - minOf(r, g, b)
    val h = when {
        c == 0.0 -> 0.0
        v == r -> (g - b) / c
        v == g -> 2 + (b - r) / c
        else -> 4 + (r - g) / c
    }
    return doubleArrayOf(DEGREES_PER_SECTOR * (if (h < 0) h + SECTORS else h), if (v == 0.0) 0.0 else c / v, v)
}

/** Port of `hsv2rgb`. */
private fun hsv2rgb(hsv: DoubleArray): DoubleArray {
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    fun f(n: Int): Double {
        val k = (n + h / DEGREES_PER_SECTOR) % SECTORS
        return v - v * s * maxOf(minOf(k, HSV_RAMP_END - k, 1.0), 0.0)
    }
    return doubleArrayOf(f(HSV_RED), f(HSV_GREEN), f(HSV_BLUE))
}

/** Port of `rgb2hex`. */
private fun rgb2hex(rgb: DoubleArray): String = "#" + rgb.joinToString("") {
    Math.round(it.coerceIn(0.0, RGB_MAX)).toString(HEX_RADIX).padStart(2, '0')
}

private const val RGB_SIZE = 3

// The `n` of each channel and the end of the ramp in upstream's `hsv2rgb` formula
private const val HSV_RED = 5
private const val HSV_GREEN = 3
private const val HSV_BLUE = 1
private const val HSV_RAMP_END = 4
private const val RGB_MAX = 255.0
private const val HEX_RADIX = 16
private const val DEGREES_PER_SECTOR = 60
private const val SECTORS = 6
private const val MIN_SATURATION = 0.4
private const val WHITE_SATURATION = 0.1
private const val WHITE_VALUE = 225.0
