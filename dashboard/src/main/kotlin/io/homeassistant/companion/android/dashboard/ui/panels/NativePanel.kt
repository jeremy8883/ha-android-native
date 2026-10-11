package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import java.time.ZonedDateTime

/** A panel drawn natively that isn't a dashboard, by its [name] (one of `NATIVE_PANELS`). */
@Composable
internal fun NativePanel(
    name: String,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    when (name) {
        TODO -> TodoPanel(hass, now, interactions)
    }
}

private const val TODO = "todo"
