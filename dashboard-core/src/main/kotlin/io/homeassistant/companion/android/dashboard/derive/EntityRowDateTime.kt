package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of `hui-input-datetime-entity-row`, `hui-date-entity-row`, `hui-time-entity-row` and
// `hui-datetime-entity-row` (frontend@20260624.6 src/panels/lovelace/entity-rows/) with `setInputDateTimeValue`,
// `setDateValue`, `setTimeValue` and `setDateTimeValue` (src/data/).

/**
 * A date and/or time row: [date] as "yyyy-MM-dd" and [time] as "HH:mm:ss" (each `null` when the entity has none
 * or doesn't know it, and absent from [hasDate]/[hasTime] rows), with the entity's name inside the date field when
 * both show ([label]). [dateText] and [timeText] are how they read in the user's language, in [zone].
 */
data class RowDateTime(
    val label: String?,
    val hasDate: Boolean,
    val hasTime: Boolean,
    val date: String?,
    val time: String?,
    val enabled: Boolean,
    private val kind: DateTimeKind,
    private val entityId: String,
    val dateText: String? = null,
    val timeText: String? = null,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : RowControl {
    /** The call setting the date to [date] ("yyyy-MM-dd"), keeping the time; `null` when it can't. */
    fun setDate(date: String): CardAction.CallService? = when (kind) {
        DateTimeKind.Helper -> call(
            "input_datetime",
            "set_datetime",
            listOfNotNull(
                time?.let { "time" to it },
                "date" to date,
            ),
        )
        DateTimeKind.Date -> call("date", "set_value", listOf("date" to date))
        DateTimeKind.Time -> null
        is DateTimeKind.DateTime -> kind.instant?.atZone(zone)?.let { current ->
            val day = LocalDate.parse(date)
            dateTimeCall(
                current.withYear(day.year).withMonth(day.monthValue).withDayOfMonth(day.dayOfMonth).toInstant(),
            )
        }
    }

    /** The call setting the time to [time] ("HH:mm:ss"), keeping the date; `null` when it can't. */
    fun setTime(time: String): CardAction.CallService? = when (kind) {
        DateTimeKind.Helper -> call(
            "input_datetime",
            "set_datetime",
            listOfNotNull(
                "time" to time,
                date?.let {
                    "date" to
                        it
                },
            ),
        )
        DateTimeKind.Time -> call("time", "set_value", listOf("time" to time))
        DateTimeKind.Date -> null
        is DateTimeKind.DateTime -> kind.instant?.atZone(zone)?.let { current ->
            val clock = LocalTime.parse(time)
            dateTimeCall(current.withHour(clock.hour).withMinute(clock.minute).withSecond(clock.second).toInstant())
        }
    }

    private fun call(domain: String, service: String, values: List<Pair<String, String>>) = CardAction.CallService(
        domain,
        service,
        JsonObject(
            mapOf("entity_id" to JsonPrimitive(entityId)) + values.map { (key, value) -> key to JsonPrimitive(value) },
        ),
        target = null,
    )

    /** `setDateTimeValue`: the moment as `toISOString()`, in UTC with milliseconds. */
    private fun dateTimeCall(instant: Instant) =
        call("datetime", "set_value", listOf("datetime" to ISO_MILLIS.format(instant.atZone(ZoneId.of("UTC")))))
}

/** Which entity a date and time row sets. */
sealed interface DateTimeKind {
    /** An `input_datetime` helper. */
    data object Helper : DateTimeKind

    /** A `date` entity. */
    data object Date : DateTimeKind

    /** A `time` entity. */
    data object Time : DateTimeKind

    /** A `datetime` entity, at [instant] (`null` when unknown). */
    data class DateTime(val instant: Instant?) : DateTimeKind
}

/**
 * The date and time row of [state], `null` for another domain. A datetime shows in the dashboard's time zone (the
 * browser's own upstream, which the dashboard's follows unless set to the server's).
 */
internal fun HassSnapshot.dateTimeRowControl(state: EntityState, name: String): RowDateTime? =
    dateTimeRow(state, name)?.let { row ->
        val zone = formats.zone
        row.copy(
            zone = zone,
            dateText = row.date?.let { formats.date(LocalDate.parse(it).atStartOfDay(zone).toInstant(), zone) },
            timeText = row.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?.let { formats.time(LocalDate.of(YEAR, 1, 1).atTime(it).atZone(zone).toInstant(), zone) },
        )
    }

private fun HassSnapshot.dateTimeRow(state: EntityState, name: String): RowDateTime? {
    val zone = formats.zone
    val known = state.state != STATE_UNAVAILABLE && state.state != STATE_UNKNOWN
    val enabled = state.state != STATE_UNAVAILABLE
    return when (state.domain) {
        "input_datetime" -> helperDateTime(state, name)
        "date" -> RowDateTime(
            null,
            true,
            false,
            state.state.takeIf {
                known
            },
            null,
            enabled,
            DateTimeKind.Date,
            state.entityId,
        )
        "time" -> RowDateTime(
            null,
            false,
            true,
            null,
            state.state.takeIf {
                known
            },
            enabled,
            DateTimeKind.Time,
            state.entityId,
        )
        "datetime" -> {
            val instant = state.state.takeIf { known }?.let { parseJsDate(it, zone) }
            val local = instant?.atZone(zone)
            RowDateTime(
                label = name,
                hasDate = true,
                hasTime = true,
                date = local?.toLocalDate()?.toString(),
                time = local?.toLocalTime()?.format(TIME),
                enabled = enabled,
                kind = DateTimeKind.DateTime(instant),
                entityId = state.entityId,
            )
        }
        else -> null
    }
}

/** An `input_datetime`'s row: its date and/or time, from "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd" or "HH:mm:ss". */
private fun helperDateTime(state: EntityState, name: String): RowDateTime {
    val hasDate = state.attributes.boolean("has_date") == true
    val hasTime = state.attributes.boolean("has_time") == true
    val parts = state.state.split(" ")
    val known = state.state != STATE_UNKNOWN
    return RowDateTime(
        label = name.takeIf { hasDate && hasTime },
        hasDate = hasDate,
        hasTime = hasTime,
        date = parts[0].takeIf { hasDate && known },
        time = (if (hasDate) parts.getOrNull(1) else parts[0]).takeIf { hasTime && known },
        enabled = state.state != STATE_UNAVAILABLE,
        kind = DateTimeKind.Helper,
        entityId = state.entityId,
    )
}

private const val YEAR = 2000
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ISO_MILLIS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
