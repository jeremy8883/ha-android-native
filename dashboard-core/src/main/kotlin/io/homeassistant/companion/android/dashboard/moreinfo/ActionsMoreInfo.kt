package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of `more-info-counter` and `more-info-automation` (frontend@20260624.6 src/dialogs/more-info/controls/):
// plain buttons under the details. The timer's are in TimerMoreInfo.kt.

/** A plain text button under the details. */
data class MoreInfoAction(val label: String, val enabled: Boolean, val action: CardAction.CallService)

/** An automation's details: when it last ran, and the button running its actions. */
data class AutomationMoreInfo(val lastTriggered: String, val run: MoreInfoAction)

/** A counter's increment, decrement and reset buttons, each disabled at its limit, or `null` for another entity. */
fun HassSnapshot.counterActions(state: EntityState): List<MoreInfoAction>? {
    if (state.domain != COUNTER) return null
    val available = state.state != UNAVAILABLE
    val value = state.state.toDoubleOrNull()
    val atLimit = { limit: String -> value != null && value == state.attributes.numberOrNull(limit) }
    return listOf(
        MoreInfoAction(label("increment"), available && !atLimit("maximum"), call(state, "increment")),
        MoreInfoAction(label("decrement"), available && !atLimit("minimum"), call(state, "decrement")),
        MoreInfoAction(label("reset"), available, call(state, "reset")),
    )
}

/**
 * An automation's details at [now]: "Last triggered: 18 minutes ago" ("Never" without a run), and "Run actions",
 * which skips its conditions; `null` for another entity.
 */
fun HassSnapshot.automationMoreInfo(state: EntityState, now: Instant): AutomationMoreInfo? {
    if (state.domain != AUTOMATION) return null
    val last = state.attributes.string("last_triggered")?.let { parseJsDate(it, ZoneOffset.UTC) }
    val time = last?.let { formats.relativeTime(minOf(it, now), now) } ?: localize("ui.components.relative_time.never")
    return AutomationMoreInfo(
        lastTriggered = "${localize("ui.card.automation.last_triggered")}: ${time.replaceFirstChar {
            it.uppercaseChar()
        }}",
        run = MoreInfoAction(
            localize("ui.card.automation.trigger"),
            state.state != UNAVAILABLE,
            CardAction.CallService(
                AUTOMATION,
                "trigger",
                JsonObject(entityData(state) + ("skip_condition" to JsonPrimitive(true))),
                target = null,
            ),
        ),
    )
}

private fun HassSnapshot.label(action: String) = localize("ui.card.counter.actions.$action")

private fun call(state: EntityState, service: String) =
    CardAction.CallService(state.domain, service, entityData(state), target = null)

private const val COUNTER = "counter"
private const val AUTOMATION = "automation"
private const val UNAVAILABLE = "unavailable"
