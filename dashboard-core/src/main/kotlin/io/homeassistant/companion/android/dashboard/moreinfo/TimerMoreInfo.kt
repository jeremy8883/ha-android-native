package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Port of `more-info-timer` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-timer.ts) with
// `createDurationData` (src/common/datetime/create_duration_data.ts).

/** A duration as `ha-duration-input` edits it. */
data class TimerDuration(val hours: Int, val minutes: Int, val seconds: Int, val milliseconds: Int)

/**
 * A timer's details: the duration to start with (seeded from the configured one) and its buttons for the state:
 * start when idle; set (restart with the duration), pause or start, cancel and finish while running or paused.
 */
data class TimerMoreInfo(val duration: TimerDuration?, val buttons: List<TimerButton>)

/** One of a timer's buttons. */
sealed interface TimerButton {
    val label: String

    /** (Re)starts the timer with the entered duration. */
    data class Start(override val label: String) : TimerButton

    /** Calls [action] as it is. */
    data class Call(override val label: String, val action: CardAction.CallService) : TimerButton
}

/** The details of a timer, or `null` for another entity. */
fun HassSnapshot.timerMoreInfo(state: EntityState): TimerMoreInfo? {
    if (state.domain != TIMER) return null
    val label = { action: String -> localize("ui.card.timer.actions.$action") }
    val call = { action: String ->
        TimerButton.Call(label(action), CardAction.CallService(TIMER, action, entityData(state), null))
    }
    val running = state.state == ACTIVE || state.state == PAUSED
    return TimerMoreInfo(
        duration = state.attributes.string("duration")?.let(::parseDuration),
        buttons = listOfNotNull(
            TimerButton.Start(label("start")).takeIf { state.state == IDLE },
            TimerButton.Start(label("set")).takeIf { running },
            call("pause").takeIf { state.state == ACTIVE },
            call("start").takeIf { state.state == PAUSED },
        ) + if (running) listOf(call("cancel"), call("finish")) else emptyList(),
    )
}

/** The call starting [state] with [duration], or its own when there's none. */
fun timerStartCall(state: EntityState, duration: TimerDuration?): CardAction.CallService {
    val data = duration?.let {
        mapOf(
            "duration" to buildJsonObject {
                put("hours", it.hours)
                put("minutes", it.minutes)
                put("seconds", it.seconds)
                put("milliseconds", it.milliseconds)
            },
        )
    }.orEmpty()
    return CardAction.CallService(TIMER, "start", JsonObject(entityData(state) + data), target = null)
}

/** Port of `createDurationData` for "h:mm:ss(.fff)" texts; `null` with more than three parts. */
internal fun parseDuration(text: String): TimerDuration? {
    val parts = text.split(":")
    val number = { index: Int -> parts.getOrNull(index)?.toDoubleOrNull() ?: 0.0 }
    // A single part is seconds alone
    val seconds = if (parts.size == 1) Math.floor(number(0)) else number(2)
    val whole = Math.floor(seconds)
    return TimerDuration(
        hours = if (parts.size == 1) 0 else number(0).toInt(),
        minutes = if (parts.size == 1) 0 else number(1).toInt(),
        seconds = whole.toInt(),
        milliseconds = Math.floor(Math.round((seconds - whole) * FRACTION) / FRACTION * MILLIS).toInt(),
    ).takeIf { parts.size <= MAX_PARTS }
}

private const val TIMER = "timer"
private const val IDLE = "idle"
private const val ACTIVE = "active"
private const val PAUSED = "paused"
private const val MAX_PARTS = 3
private const val FRACTION = 10_000.0
private const val MILLIS = 1000.0
