package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** The servers to switch to, as the companion app's `ServerChooser` sheet; [onSelected] gets the one chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSwitcherSheet(onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val viewModel: ServersViewModel = hiltViewModel()
    val servers by viewModel.items.collectAsStateWithLifecycle()
    HAModalBottomSheet(bottomSheetState = rememberModalBottomSheetState(), onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = HADimens.SPACE4)) {
            val list = servers ?: return@Column HALoading(Modifier.padding(HADimens.SPACE4))
            list.forEach { server ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onSelected(server.id) }
                        .padding(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE3),
                ) {
                    val color = LocalHAColorScheme.current.let {
                        if (server.active) it.colorOnPrimaryNormal else it.colorTextPrimary
                    }
                    Text(server.name, style = HATextStyle.Body, color = color)
                    server.userName?.let { Text(it, style = HATextStyle.BodyMedium) }
                }
            }
        }
    }
}
