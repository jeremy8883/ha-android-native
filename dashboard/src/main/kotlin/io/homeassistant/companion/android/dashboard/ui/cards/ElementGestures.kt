package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.Gesture
import kotlinx.serialization.json.JsonObject

/**
 * What cards report to the dashboard.
 *
 * @property onGesture a gesture on an element, with the element's action config
 * @property onAction an action a control runs directly, such as a feature's service call
 */
@Immutable
internal data class CardInteractions(
    val onGesture: (JsonObject, Gesture) -> Unit,
    val onAction: (CardAction) -> Unit,
) {
    companion object {
        val NONE = CardInteractions(onGesture = { _, _ -> }, onAction = {})
    }
}

/**
 * Make an element respond to the gestures [actions] defines: tap, long press for `hold_action` and double tap
 * for `double_tap_action`. Double tap is only listened to when configured, so taps stay immediate otherwise.
 */
internal fun Modifier.elementGestures(actions: ElementActions, interactions: CardInteractions): Modifier =
    if (!actions.interactive) {
        this
    } else {
        val onGesture = interactions.onGesture
        combinedClickable(
            role = Role.Button,
            onClick = { if (actions.tap) onGesture(actions.config, Gesture.TAP) },
            onLongClick = if (actions.hold) ({ onGesture(actions.config, Gesture.HOLD) }) else null,
            onDoubleClick = if (actions.doubleTap) ({ onGesture(actions.config, Gesture.DOUBLE_TAP) }) else null,
        )
    }
