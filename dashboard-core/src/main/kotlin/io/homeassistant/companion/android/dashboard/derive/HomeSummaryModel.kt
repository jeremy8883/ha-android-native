package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.home.HomeSummary
import java.math.BigDecimal

/**
 * Display-ready content of the tile-style info cards (home summary, repairs, updates, discovered devices),
 * which upstream all draw with `ha-tile-container`.
 *
 * @property color the colour name (`amber`, `deep-orange`, `warning`, ...), as upstream sets `--tile-color`
 * @property secondary the summary line ("3 on", "2 updates"); empty when there is nothing to say
 * @property loading whether [secondary] is still being loaded
 */
data class InfoTileModel(
    val label: String,
    val icon: String,
    val color: String,
    val secondary: String,
    val loading: Boolean,
    val vertical: Boolean,
)

/**
 * Derive a `home-summary` card, or `null` for an unknown `summary`.
 * Port of `HuiHomeSummaryCard` (frontend@20260624.6 src/panels/lovelace/cards/hui-home-summary-card.ts). Energy
 * needs the energy statistics, which are not fetched yet, so it stays loading.
 */
fun HassSnapshot.homeSummaryModel(card: CardConfig): InfoTileModel? {
    val summary = HomeSummary.entries.firstOrNull { it.key == card.json.string("summary") } ?: return null
    return InfoTileModel(
        label = summary.label(localize),
        icon = summary.icon,
        color = summary.color,
        secondary = summaryState(summary),
        loading = summary == HomeSummary.ENERGY,
        vertical = card.json.boolean("vertical") == true,
    )
}

/** Port of `_computeSummaryState`. */
private fun HassSnapshot.summaryState(summary: HomeSummary): String {
    val entities by lazy { findEntities(states.keys.toList(), summary.filters) }
    fun withState(vararg values: String) = entities.count { states[it]?.state in values }
    fun count(key: String, count: Int) = localize("ui.card.home-summary.$key", mapOf("count" to count.toString()))
    return when (summary) {
        HomeSummary.LIGHT -> withState("on").let {
            if (it >
                0
            ) {
                count("count_lights_on", it)
            } else {
                text("all_lights_off")
            }
        }
        HomeSummary.CLIMATE -> climateSummary()
        HomeSummary.SECURITY -> {
            val locks = entities.filter { it.substringBefore('.') == "lock" }
            val alarms = entities.filter { it.substringBefore('.') == "alarm_control_panel" }
            val unlocked = locks.count { states[it]?.state in UNSECURED_LOCK_STATES }
            val disarmed = alarms.count { states[it]?.state == "disarmed" }
            when {
                locks.isEmpty() && alarms.isEmpty() -> ""
                unlocked > 0 -> count("count_locks_unlocked", unlocked)
                disarmed > 0 -> count("count_alarms_disarmed", disarmed)
                else -> text("all_secure")
            }
        }
        HomeSummary.MEDIA_PLAYERS ->
            withState("playing").let { if (it > 0) count("count_media_playing", it) else text("no_media_playing") }
        HomeSummary.MAINTENANCE -> {
            val low = lowBatteryEntities(entities).size
            val unavailable = entities.count { states[it]?.state == STATE_UNAVAILABLE }
            val parts = listOfNotNull(
                count("count_maintenance_low_battery_issues", low).takeIf { low > 0 },
                count("count_maintenance_issues_unavailable_battery_entities", unavailable).takeIf { unavailable > 0 },
            )
            parts.joinToString(", ").ifEmpty { text("all_maintenance_good") }
        }
        HomeSummary.ENERGY -> ""
        HomeSummary.PERSONS ->
            withState("home").let { if (it > 0) count("count_persons_home", it) else text("nobody_home") }
    }
}

private fun HassSnapshot.text(key: String) = localize("ui.card.home-summary.$key")

/** The lowest and highest area temperatures: "19.4 - 22.8°", or "21.0°" when they are the same. */
private fun HassSnapshot.climateSummary(): String {
    // `parseFloat(state) || NaN`: upstream also drops readings of exactly 0
    val values = registries.areas.values.mapNotNull { area ->
        area.temperatureEntityId?.ifEmpty { null }?.let { states[it]?.state }?.let(::jsParseFloat)
            ?.takeUnless { it.isNaN() || it == 0.0 }
    }
    if (values.isEmpty()) return ""
    val min = formats.number(BigDecimal.valueOf(values.min()), 1, 1)
    val max = formats.number(BigDecimal.valueOf(values.max()), 1, 1)
    return if (min == max) "$min°" else "$min - $max°"
}

/**
 * Battery entities at or below [LOW_BATTERY_THRESHOLD] percent (or `on` battery binary sensors), skipping
 * devices that are charging. Port of `filterLowBatteryEntities`
 * (src/panels/maintenance/strategies/maintenance-view-strategy.ts).
 */
internal fun HassSnapshot.lowBatteryEntities(entityIds: List<String>): List<String> = entityIds.filter { entityId ->
    val state = states[entityId]?.state.orEmpty()
    if (entityId.substringBefore('.') == "binary_sensor") return@filter state == "on"
    val deviceId = registries.entities[entityId]?.deviceId
    val charging = deviceId?.let { id ->
        registries.entities.values.firstOrNull {
            it.deviceId == id && states[it.entityId]?.attributes?.string("device_class") == "battery_charging"
        }
    }
    if (charging != null && states[charging.entityId]?.state == "on") return@filter false
    val value = jsParseFloat(state)
    !value.isNaN() && value <= LOW_BATTERY_THRESHOLD
}

/** JavaScript `parseFloat`: the longest leading decimal number, NaN when there is none. */
internal fun jsParseFloat(text: String): Double {
    val match = LEADING_NUMBER.find(text.trimStart()) ?: return Double.NaN
    return match.value.toDoubleOrNull() ?: Double.NaN
}

private const val LOW_BATTERY_THRESHOLD = 20
private val UNSECURED_LOCK_STATES = setOf("unlocked", "jammed", "open")
private val LEADING_NUMBER = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")
