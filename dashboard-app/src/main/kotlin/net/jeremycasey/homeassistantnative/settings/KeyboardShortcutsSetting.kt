package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import net.jeremycasey.homeassistantnative.R

/**
 * The switch turning keyboard shortcuts on or off, with the frontend profile's texts.
 *
 * Upstream: `ha-enable-shortcuts-row` (src/panels/profile/ha-enable-shortcuts-row.ts)
 */
@Composable
internal fun KeyboardShortcutsSetting() {
    val viewModel: KeyboardShortcutsSettingViewModel = hiltViewModel()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = viewModel::onEnabledChange)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_shortcuts),
                style = HATextStyle.Body.copy(
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    textAlign = TextAlign.Start,
                ),
            )
            Text(
                stringResource(R.string.settings_shortcuts_description),
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            )
        }
        // The row toggles it, as one control for accessibility
        HASwitch(
            checked = enabled,
            onCheckedChange = viewModel::onEnabledChange,
            modifier = Modifier.clearAndSetSemantics {
            },
        )
    }
}
