package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.slugify
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull

// The timeline chart of an entity's states. Port of `_generateData` of state-history-chart-timeline.ts and of
// timeline-color.ts (frontend@20260624.6 src/components/chart/).

/** An entity's timeline from [TimelineBand.start] of its first state to the chart's end. */
data class TimelineChart(val entityId: String, val name: String, val bands: List<TimelineBand>)

/** A state from [start] to [end] (epoch ms), [label] being how it reads. */
data class TimelineBand(
    val start: Double,
    val end: Double,
    val state: String,
    val label: String,
    val color: TimelineColor,
)

/**
 * A band's colour, the first of: the first of [variables] the theme defines (brightened by [shade] in Lab
 * lightness steps), the graph palette's colour at [paletteIndex], then a palette colour picked for the [state] the
 * first time it shows (the same for every timeline). Port of `computeTimelineColor`.
 */
data class TimelineColor(val variables: List<String>, val shade: Double?, val paletteIndex: Int?, val state: String)

/** The timeline of [entity] up to [end] (epoch ms); states after it are left out. */
fun HassSnapshot.timelineChart(entity: TimelineEntity, end: Double): TimelineChart {
    val bands = mutableListOf<TimelineBand>()
    var previous: TimelineState? = null
    var previousStart = 0.0
    entity.data.forEach { state ->
        val time = state.lastChanged.toDouble()
        if (time > end) return@forEach
        val prev = previous
        if (prev == null) {
            previous = state
            previousStart = time
        } else if (state.state != prev.state) {
            bands +=
                TimelineBand(
                    previousStart,
                    time,
                    prev.state,
                    prev.stateLocalize,
                    timelineColor(entity.entityId, prev.state),
                )
            previous = state
            previousStart = time
        }
    }
    previous?.let { prev ->
        bands +=
            TimelineBand(previousStart, end, prev.state, prev.stateLocalize, timelineColor(entity.entityId, prev.state))
    }
    return TimelineChart(entity.entityId, entity.name, bands)
}

/** Port of `computeTimelineColor`, the theme-dependent parts left to resolve. */
internal fun HassSnapshot.timelineColor(entityId: String, state: String): TimelineColor {
    val current = states[entityId]
    val domain = entityId.substringBefore('.')
    val variables = when {
        current == null || state == STATE_UNAVAILABLE -> listOf("history-unavailable-color")
        state == STATE_UNKNOWN -> listOf("history-unknown-color")
        // Zones have no colours of their own unless the theme gives them one
        domain in ZONE_DOMAINS && state !in FIXED_DOMAIN_STATES[domain].orEmpty() ->
            listOf("state-$domain-${slugify(state)}-color")
        else -> (stateColor(current, state) as? DisplayColor.State)?.variables.orEmpty()
    }
    return TimelineColor(
        variables = variables,
        shade = TIMELINE_SHADES[domain]?.get(state).takeIf { current != null && state != STATE_UNKNOWN },
        paletteIndex = current?.let { enumIndex(it, state) },
        state = state,
    )
}

/** The state's place among its entity's possible states, which picks its palette colour. */
private fun enumIndex(current: EntityState, state: String): Int? {
    val domain = current.domain
    val enumSensor = domain == "sensor" && current.attributes.string("device_class") == "enum"
    val options = FIXED_DOMAIN_STATES[domain] ?: if (enumSensor || domain in SELECT_DOMAINS) {
        current.attributes.array("options")?.mapNotNull { it.stringOrNull }
    } else {
        null
    }
    return options?.indexOf(state)?.takeIf { it >= 0 }
}

private const val STATE_UNAVAILABLE = "unavailable"
private const val STATE_UNKNOWN = "unknown"
private val ZONE_DOMAINS = setOf("person", "device_tracker")
private val SELECT_DOMAINS = setOf("select", "input_select")
private const val HALF_SHADE = 0.5
private val TIMELINE_SHADES = mapOf(
    "media_player" to mapOf("paused" to HALF_SHADE, "idle" to 1.0),
    "vacuum" to mapOf("returning" to HALF_SHADE),
)

/** Port of `FIXED_DOMAIN_STATES` (src/common/entity/get_states.ts). */
internal val FIXED_DOMAIN_STATES: Map<String, List<String>> = mapOf(
    "alarm_control_panel" to listOf(
        "armed_away",
        "armed_custom_bypass",
        "armed_home",
        "armed_night",
        "armed_vacation",
        "arming",
        "disarmed",
        "disarming",
        "pending",
        "triggered",
    ),
    "alert" to listOf("on", "off", "idle"),
    "assist_satellite" to listOf("idle", "listening", "responding", "processing"),
    "automation" to listOf("on", "off"),
    "binary_sensor" to listOf("on", "off"),
    "calendar" to listOf("on", "off"),
    "camera" to listOf("idle", "recording", "streaming"),
    "cover" to listOf("closed", "closing", "open", "opening"),
    "device_tracker" to listOf("home", "not_home"),
    "fan" to listOf("on", "off"),
    "humidifier" to listOf("on", "off"),
    "input_boolean" to listOf("on", "off"),
    "lawn_mower" to listOf("error", "paused", "mowing", "returning", "docked"),
    "light" to listOf("on", "off"),
    "lock" to listOf("jammed", "locked", "locking", "unlocked", "unlocking", "opening", "open"),
    "media_player" to listOf("off", "on", "idle", "playing", "paused", "standby", "buffering"),
    "person" to listOf("home", "not_home"),
    "plant" to listOf("ok", "problem"),
    "radio_frequency" to emptyList(),
    "remote" to listOf("on", "off"),
    "schedule" to listOf("on", "off"),
    "script" to listOf("on", "off"),
    "siren" to listOf("on", "off"),
    "sun" to listOf("above_horizon", "below_horizon"),
    "switch" to listOf("on", "off"),
    "timer" to listOf("active", "idle", "paused"),
    "update" to listOf("on", "off"),
    "vacuum" to listOf("cleaning", "docked", "error", "idle", "paused", "returning"),
    "valve" to listOf("closed", "closing", "open", "opening"),
    "weather" to listOf(
        "clear-night",
        "cloudy",
        "exceptional",
        "fog",
        "hail",
        "lightning-rainy",
        "lightning",
        "partlycloudy",
        "pouring",
        "rainy",
        "snowy-rainy",
        "snowy",
        "sunny",
        "windy-variant",
        "windy",
    ),
)
