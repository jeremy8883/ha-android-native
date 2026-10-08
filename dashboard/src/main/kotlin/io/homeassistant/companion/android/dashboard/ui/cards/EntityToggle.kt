package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * The switch of an entity that turns on and off. Like upstream's `ha-entity-toggle`
 * (frontend@20260624.6 src/components/entity/ha-entity-toggle.ts), it moves as soon as it is tapped, and moves back
 * when no new state arrived within 2 seconds, for example because the action failed.
 *
 * @param updatedAt when the entity's state object last changed; a change shows the entity's state again
 */
@Composable
internal fun EntityToggle(checked: Boolean, updatedAt: Double, onToggle: () -> Unit, enabled: Boolean = true) {
    var tapped by remember(updatedAt) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(tapped) {
        if (tapped != null) {
            delay(TOGGLE_RESET_DELAY)
            tapped = null
        }
    }
    Switch(
        checked = tapped ?: checked,
        onCheckedChange = {
            tapped = it
            onToggle()
        },
        enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting),
    )
}

private val TOGGLE_RESET_DELAY = 2.seconds
