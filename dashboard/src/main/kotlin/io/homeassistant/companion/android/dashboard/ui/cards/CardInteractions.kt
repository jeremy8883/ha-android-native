package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.dashboard.action.CardAction
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
