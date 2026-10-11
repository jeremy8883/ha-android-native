package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import net.jeremycasey.homeassistantnative.R

/**
 * The app's settings: the servers it's logged in to (to switch to, log out of, or add another).
 *
 * @param onAddServer opens the login for another server
 */
@Composable
internal fun SettingsScreen(onBack: () -> Unit, onAddServer: () -> Unit) {
    val servers: ServersViewModel = hiltViewModel()
    val items by servers.items.collectAsStateWithLifecycle()
    var loggingOut by rememberSaveable { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = { HATopBar(title = { Text(stringResource(R.string.settings_title)) }, onBackClick = onBack) },
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_servers))
            val list = items
            if (list == null) {
                HALoading(Modifier.align(Alignment.CenterHorizontally).padding(HADimens.SPACE4))
            } else {
                list.forEach { server ->
                    ServerRow(server, onClick = { servers.onActivate(server.id) }) {
                        HAPlainButton(stringResource(R.string.settings_log_out), { loggingOut = server.id })
                    }
                }
            }
            HAPlainButton(
                stringResource(R.string.settings_add_server),
                onAddServer,
                Modifier.padding(horizontal = HADimens.SPACE2),
            )
        }
    }
    loggingOut?.let { serverId ->
        LogOutDialog(
            serverName = items?.firstOrNull { it.id == serverId }?.name.orEmpty(),
            onConfirm = {
                loggingOut = null
                servers.onLogOut(serverId)
            },
            onDismiss = { loggingOut = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        modifier = Modifier.padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
    )
}

@Composable
private fun LogOutDialog(serverName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_log_out_title, serverName)) },
        text = { Text(stringResource(R.string.settings_log_out_text)) },
        confirmButton = { HAPlainButton(stringResource(R.string.settings_log_out), onConfirm) },
        dismissButton = { HAPlainButton(stringResource(android.R.string.cancel), onDismiss) },
    )
}
