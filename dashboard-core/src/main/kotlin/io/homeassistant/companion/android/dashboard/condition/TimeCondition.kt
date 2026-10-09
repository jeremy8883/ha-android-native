package io.homeassistant.companion.android.dashboard.condition

import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** Port of `checkTimeInRange` (src/common/datetime/check_time.ts). Without a clock the condition is not met. */
internal fun timeCondition(condition: JsonObject, now: ZonedDateTime?): Boolean {
    now ?: return false
    val weekdays = condition.array("weekdays")?.mapNotNull { it.stringOrNull }.orEmpty()
    val today = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
    return (weekdays.isEmpty() || today in weekdays) && timeInRange(condition, now)
}

private fun timeInRange(condition: JsonObject, now: ZonedDateTime): Boolean {
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
    val hours = parts.getOrNull(0)
    val minutes = parts.getOrNull(1)
    val seconds = if (parts.size == TIME_WITH_SECONDS) parts[2] else 0
    if (hours == null || minutes == null || seconds == null) return null
    return runCatching { LocalTime.of(hours, minutes, seconds) }.getOrNull()
}

/** Parts of a time written with seconds (`HH:MM:SS`). */
private const val TIME_WITH_SECONDS = 3
