package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.moreinfo.SirenAdvanced
import io.homeassistant.companion.android.dashboard.moreinfo.SirenMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.sirenTurnOnCall
import io.homeassistant.companion.android.dashboard.ui.controls.ControlButtonIcon
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl

/**
 * The controls of a siren's details, port of `more-info-siren` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-siren.ts): the tall switch, and "More controls" opening its tone,
 * volume and duration.
 */
@Composable
internal fun MoreInfoSiren(info: SirenMoreInfo, state: EntityState, onAction: (CardAction) -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StateToggleControl(info.toggle, onAction)
        info.advanced?.let { controls ->
            HAPlainButton(controls.title, { advanced = true })
            if (advanced) SirenAdvancedDialog(controls, state, onAction) { advanced = false }
        }
    }
}

/** Port of `ha-more-info-siren-advanced-controls`: the options, then buttons turning the siren on with them or off. */
@Composable
private fun SirenAdvancedDialog(
    controls: SirenAdvanced,
    state: EntityState,
    onAction: (CardAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var tone by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(controls.title, style = HATextStyle.HeadlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                controls.tones?.let { tones ->
                    HADropdownMenu(
                        items = tones.map { (value, name) -> HADropdownItem(value, name) },
                        selectedKey = tone,
                        onItemSelected = { tone = it },
                        label = controls.toneLabel,
                    )
                }
                if (controls.volume) NumberField(controls.volumeLabel, volume, "%") { volume = it }
                if (controls.duration) NumberField(controls.durationLabel, duration, "s") { duration = it }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterHorizontally),
                ) {
                    ControlButtonIcon("mdi:play", controls.turnOnLabel, Modifier.size(BUTTON_SIZE)) {
                        // Upstream keeps a volume or duration that isn't a number unset
                        val level = volume.toDoubleOrNull()?.let { it / PERCENT }
                        onAction(sirenTurnOnCall(state, tone, level, duration.toIntOrNull()))
                    }
                    ControlButtonIcon("mdi:stop", controls.turnOffLabel, Modifier.size(BUTTON_SIZE)) {
                        onAction(controls.turnOff)
                    }
                }
            }
        },
        confirmButton = { HAPlainButton(controls.closeLabel, onDismiss) },
    )
}

@Composable
private fun NumberField(label: String, value: String, unit: String, onChange: (String) -> Unit) {
    HATextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        trailingIcon = { Text(unit) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val PERCENT = 100.0
private val BUTTON_SIZE = 64.dp
