package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.Gesture
import kotlinx.serialization.json.JsonObject

/** Called with an element's action config and the gesture the user made on it. */
internal typealias OnGesture = (JsonObject, Gesture) -> Unit

/**
 * Make an element respond to the gestures [actions] defines: tap, long press for `hold_action` and double tap
 * for `double_tap_action`. Double tap is only listened to when configured, so taps stay immediate otherwise.
 */
internal fun Modifier.elementGestures(actions: ElementActions, onGesture: OnGesture): Modifier =
    if (!actions.interactive) {
        this
    } else {
        combinedClickable(
            role = Role.Button,
            onClick = { if (actions.tap) onGesture(actions.config, Gesture.TAP) },
            onLongClick = if (actions.hold) ({ onGesture(actions.config, Gesture.HOLD) }) else null,
            onDoubleClick = if (actions.doubleTap) ({ onGesture(actions.config, Gesture.DOUBLE_TAP) }) else null,
        )
    }
