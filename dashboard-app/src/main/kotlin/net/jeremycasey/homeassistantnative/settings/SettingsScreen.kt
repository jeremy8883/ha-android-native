package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
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
 * The app's settings: the servers it's logged in to, to switch to, log out of, or add another.
 *
 * @param onAddServer opens the login for another server
 */
@Composable
internal fun SettingsScreen(onBack: () -> Unit, onAddServer: () -> Unit) {
    val viewModel: ServersViewModel = hiltViewModel()
    val servers by viewModel.items.collectAsStateWithLifecycle()
    var loggingOut by rememberSaveable { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = { HATopBar(title = { Text(stringResource(R.string.settings_title)) }, onBackClick = onBack) },
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            Text(stringResource(R.string.settings_servers), style = HATextStyle.BodyMedium)
            val list = servers
            if (list == null) {
                HALoading(Modifier.align(Alignment.CenterHorizontally))
            } else {
                list.forEach { server ->
                    ServerRow(server, onActivate = { viewModel.onActivate(server.id) }, onLogOut = {
                        loggingOut =
                            server.id
                    })
                }
            }
            HAPlainButton(stringResource(R.string.settings_add_server), onAddServer)
        }
    }
    loggingOut?.let { serverId ->
        val name = servers?.firstOrNull { it.id == serverId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { loggingOut = null },
            title = { Text(stringResource(R.string.settings_log_out_title, name)) },
            text = { Text(stringResource(R.string.settings_log_out_text)) },
            confirmButton = {
                HAPlainButton(stringResource(R.string.settings_log_out), {
                    loggingOut = null
                    viewModel.onLogOut(serverId)
                })
            },
            dismissButton = { HAPlainButton(stringResource(android.R.string.cancel), { loggingOut = null }) },
        )
    }
}

@Composable
private fun ServerRow(server: ServerItem, onActivate: () -> Unit, onLogOut: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onActivate)
            .padding(vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(server.name, style = HATextStyle.Body)
            Text(
                listOfNotNull(server.userName, server.address).joinToString(" · "),
                style = HATextStyle.BodyMedium,
            )
            if (server.active) {
                Text(
                    stringResource(R.string.settings_active),
                    style = HATextStyle.BodyMedium,
                    color = LocalHAColorScheme.current.colorOnPrimaryNormal,
                )
            }
        }
        HAPlainButton(stringResource(R.string.settings_log_out), onLogOut)
    }
}
