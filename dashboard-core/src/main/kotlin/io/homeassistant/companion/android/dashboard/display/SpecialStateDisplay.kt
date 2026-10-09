package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor
import kotlinx.serialization.json.JsonNull

/** Port of `computeUpdateStateDisplay` (src/data/update.ts). */
internal fun HassSnapshot.updateStateDisplay(state: EntityState): String {
    val attributes = state.attributes
    val latest = attributes.string("latest_version")?.ifEmpty { null }
    val skipped = state.state == "off" && attributes.string("skipped_version") == latest
    return when {
        jsTruthy(attributes["in_progress"]) -> installingDisplay(state)
        latest != null && skipped -> latest
        else -> formatEntityState(state)
    }
}

private fun HassSnapshot.installingDisplay(state: EntityState): String {
    val attributes = state.attributes
    val percentage = attributes["update_percentage"]
    if (!state.supportsFeature(EntityFeature.UPDATE_PROGRESS) || percentage is JsonNull) {
        return localize("ui.card.update.installing")
    }
    // Upstream passes `display_precision` even when it is undefined, which leaves Intl's default of 3
    val precision = attributes["display_precision"]?.stringOrNull?.toIntOrNull()
    val number = jsNumber(percentage)
    val progress = if (number.isNaN() || number.isInfinite()) {
        number.toString()
    } else {
        formats.number(BigDecimal.valueOf(number), precision ?: 0, precision ?: INTL_DEFAULT_MAX_FRACTION_DIGITS)
    }
    return localize("ui.card.update.installing_with_progress", mapOf("progress" to progress))
}

/** Port of `ha-timer-remaining-time` with `computeDisplayTimer` (src/data/timer.ts). */
internal fun HassSnapshot.timerDisplay(state: EntityState, now: Instant): String {
    val remaining = timerTimeRemaining(state, now)
    if (state.state == "idle" || remaining == 0.0) return formatEntityState(state)
    val display = secondsToDuration(remaining ?: 0.0) ?: "0"
    return if (state.state == "paused") "$display (${formatEntityState(state)})" else display
}

private fun timerTimeRemaining(state: EntityState, now: Instant): Double? =
    state.attributes.string("remaining")?.ifEmpty { null }?.let { remaining ->
        if (state.state == "active") activeTimerRemaining(state, now) else durationSeconds(remaining)
    }

/** The seconds until an active timer finishes, NaN when its end is unknown. */
private fun activeTimerRemaining(state: EntityState, now: Instant): Double =
    state.attributes.string("finishes_at")?.let { parseJsDate(it, ZoneOffset.UTC) }
        ?.let { maxOf((it.toEpochMilli() - now.toEpochMilli()) / MILLIS_PER_SECOND, 0.0) }
        ?: Double.NaN

/** The seconds of an `H:MM:SS` duration, NaN when a part is missing or not a number. */
private fun durationSeconds(duration: String): Double {
    val parts = duration.split(":").map { jsNumber(it) }
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

private const val INTL_DEFAULT_MAX_FRACTION_DIGITS = 3
private const val SECS_PER_HOUR = 3600
private const val SECS_PER_MIN = 60
