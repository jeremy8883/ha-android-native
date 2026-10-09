package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import net.jeremycasey.homeassistantnative.R

/**
 * The servers to switch to, as the companion app's `ServerChooser` sheet (app/src/main/kotlin/io/homeassistant/
 * companion/android/settings/server/ServerChooser.kt); [onSelected] gets the one chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSwitcherSheet(onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val viewModel: ServersViewModel = hiltViewModel()
    val servers by viewModel.items.collectAsStateWithLifecycle()
    HAModalBottomSheet(bottomSheetState = rememberModalBottomSheetState(), onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.server_select),
            style = HATextStyle.HeadlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
        )
        val list = servers
        if (list == null) {
            HALoading(Modifier.align(Alignment.CenterHorizontally).padding(HADimens.SPACE4))
        } else {
            list.forEachIndexed { index, server ->
                ServerRow(server, onClick = { onSelected(server.id) })
                if (index < list.lastIndex) {
                    // Inset so the dividers start where the text starts, past the avatar
                    HAHorizontalDivider(
                        Modifier.padding(
                            start = HADimens.SPACE4 + AVATAR_SIZE + HADimens.SPACE3,
                            end = HADimens.SPACE4,
                        ),
                    )
                }
            }
        }
        Spacer(
            modifier = Modifier
                .height(HADimens.SPACE6)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }
}
