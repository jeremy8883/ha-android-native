package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset

// Port of `hui-timer-entity-row` (frontend@20260624.6 src/panels/lovelace/entity-rows/) with
// `ha-timer-remaining-time`, `timerTimeRemaining`, `computeDisplayTimer` (src/data/timer.ts) and
// `secondsToDuration` (src/common/datetime/seconds_to_duration.ts).

/**
 * A timer's time left: counting down to [finishesAt] while active, else [remaining] seconds as they stand; the
 * [state] itself when idle, at zero, or (unlike upstream's "0") unavailable ([showState]), and after the time
 * while [paused].
 */
data class RowTimer(
    val finishesAt: Instant?,
    val remaining: Double?,
    val state: String,
    val showState: Boolean,
    val paused: Boolean,
) : RowControl {
    /** What the row shows at [now]. */
    fun display(now: Instant): String {
        val left = finishesAt?.let { maxOf((it.toEpochMilli() - now.toEpochMilli()) / MILLIS, 0.0) } ?: remaining
        return when {
            showState || left == 0.0 -> state
            paused -> "${secondsToDuration(left ?: 0.0) ?: "0"} ($state)"
            else -> secondsToDuration(left ?: 0.0) ?: "0"
        }
    }

    /** Whether the display changes each second. */
    val ticking: Boolean get() = finishesAt != null
}

/** The timer row of [state], `null` for another domain. */
internal fun HassSnapshot.timerRowControl(state: EntityState): RowTimer? {
    if (state.domain != "timer") return null
    val remaining = state.attributes.string("remaining")?.let(::durationSeconds)
    return RowTimer(
        finishesAt = state.attributes.string("finishes_at")?.takeIf { state.state == "active" && remaining != null }
            ?.let { parseJsDate(it, ZoneOffset.UTC) },
        remaining = remaining,
        state = formatEntityState(state),
        showState = state.state == "idle" || state.state == STATE_UNAVAILABLE,
        paused = state.state == "paused",
    )
}

/** Port of `secondsToDuration`: "1:02:03", "2:03", "3", or `null` for none. */
fun secondsToDuration(seconds: Double): String? {
    val h = Math.floor(seconds / HOUR).toLong()
    val m = Math.floor(seconds % HOUR / MINUTE).toLong()
    val s = Math.floor(seconds % HOUR % MINUTE).toLong()
    val pad = { value: Long -> value.toString().padStart(2, '0') }
    return when {
        h > 0 -> "$h:${pad(m)}:${pad(s)}"
        m > 0 -> "$m:${pad(s)}"
        s > 0 -> "$s"
        else -> null
    }
}

/** Port of `durationToSeconds`: "h:mm:ss" as seconds. */
private fun durationSeconds(text: String): Double? {
    val parts = text.split(":").map { it.toDoubleOrNull() ?: return null }
    return parts.fold(0.0) { total, part -> total * MINUTE + part }
}

private const val MILLIS = 1000.0
private const val HOUR = 3600.0
private const val MINUTE = 60.0
