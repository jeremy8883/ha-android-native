package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.assumedState
import io.homeassistant.companion.android.dashboard.feature.lockCall

// Port of `more-info-lock` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-lock.ts) with
// `ha-state-control-lock-toggle` (src/state-control/lock/) and its helpers (src/data/lock.ts).

/**
 * What a lock's details show: the tall switch (locked at the top; lock and unlock buttons while the state is
 * unknown), or, while jammed, its pulsing icon with unlock and lock buttons under it; and an open button for locks
 * that open, which asks to be tapped again to confirm.
 */
data class LockMoreInfo(val toggle: StateToggle?, val jammed: JammedLock?, val open: LockOpen?)

/** A jammed lock: its [icon] in its [color], and the [buttons] to unlock or lock it again. */
data class JammedLock(
    val icon: String,
    val color: DisplayColor?,
    val buttons: List<Pair<String, CardAction.CallService>>,
)

/**
 * The open button: [label], then [confirmLabel] for [CONFIRM_SECONDS] after a first tap, then [doneLabel] for
 * [DONE_SECONDS] once it has opened, in the state's [color].
 */
data class LockOpen(
    val label: String,
    val confirmLabel: String,
    val doneLabel: String,
    val enabled: Boolean,
    val color: DisplayColor?,
    val action: CardAction.CallService,
) {
    companion object {
        /** How long the button waits for the confirming tap. */
        const val CONFIRM_SECONDS = 5

        /** How long the button shows that the door opened. */
        const val DONE_SECONDS = 2
    }
}

/** The details of a lock, or `null` for another entity. */
fun HassSnapshot.lockMoreInfo(state: EntityState): LockMoreInfo? {
    if (state.domain != LOCK) return null
    val jammed = state.state == JAMMED
    return LockMoreInfo(
        toggle = if (jammed) null else lockToggle(state),
        jammed = if (jammed) jammedLock(state) else null,
        open = if (state.supportsFeature(LOCK_FEATURE_OPEN)) lockOpen(state) else null,
    )
}

private fun HassSnapshot.lockToggle(state: EntityState): StateToggle {
    val on = state.state == LOCKED || state.state == LOCKING
    // Unknown, the lock shows two plain buttons
    val buttons = state.state == UNKNOWN
    val color = stateColor(state).takeUnless { buttons }
    return StateToggle(
        label = localize(if (on) "ui.card.lock.unlock" else "ui.card.lock.lock"),
        checked = on,
        offActive = false,
        showHandle = false,
        enabled = state.state != UNAVAILABLE,
        buttons = buttons,
        onColor = color,
        offColor = color,
        onIcon = entityIcon(state.entityId, stateValue = if (state.state == LOCKING) LOCKING else LOCKED).orEmpty(),
        offIcon = entityIcon(state.entityId, stateValue = if (state.state == UNLOCKING) UNLOCKING else UNLOCKED)
            .orEmpty(),
        turnOnLabel = localize("ui.card.lock.lock"),
        turnOffLabel = localize("ui.card.lock.unlock"),
        turnOn = lockCall(state, "lock"),
        turnOff = lockCall(state, "unlock"),
    )
}

private fun HassSnapshot.jammedLock(state: EntityState) = JammedLock(
    icon = entityIcon(state.entityId).orEmpty(),
    color = stateColor(state),
    buttons = listOf("unlock", "lock").map { localize("ui.card.lock.$it") to lockCall(state, it) },
)

private fun HassSnapshot.lockOpen(state: EntityState) = LockOpen(
    label = localize("ui.card.lock.open_door"),
    confirmLabel = localize("ui.card.lock.open_door_confirm"),
    doneLabel = localize("ui.card.lock.open_door_done"),
    enabled = canOpen(state),
    color = stateColor(state),
    action = lockCall(state, "open"),
)

/** Port of `canOpen`: available, and neither open nor on its way somewhere, unless only assumed. */
private fun canOpen(state: EntityState): Boolean = state.state != UNAVAILABLE &&
    (state.assumedState() || (state.state != OPEN && state.state !in WAITING))

private const val LOCK = "lock"
private const val LOCKED = "locked"
private const val LOCKING = "locking"
private const val UNLOCKED = "unlocked"
private const val UNLOCKING = "unlocking"
private const val JAMMED = "jammed"
private const val OPEN = "open"
private const val UNKNOWN = "unknown"
private const val UNAVAILABLE = "unavailable"
private const val LOCK_FEATURE_OPEN = 1
private val WAITING = setOf("opening", UNLOCKING, LOCKING)
