package io.homeassistant.companion.android.dashboard.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shows the dashboard's one-off [events]: messages as snackbars, confirmations as a dialog, links in the browser.
 *
 * @param onConfirmed runs an action the user confirmed
 * @param onShowDashboard shows the native dashboards in place of the web frontend, once one was opened
 */
@Composable
internal fun DashboardEffects(
    events: Flow<DashboardEvent>,
    snackbar: SnackbarHostState,
    onConfirmed: (CardAction) -> Unit,
    onCodeEntered: (CardAction.CallService, String) -> Unit,
    onMoreInfo: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onShowDashboard: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var confirm by remember { mutableStateOf<DashboardEvent.Confirm?>(null) }
    var codeFor by remember { mutableStateOf<CardAction.CallService?>(null) }
    // The events are collected once, so they reach the latest callbacks through these
    val currentOnMoreInfo by rememberUpdatedState(onMoreInfo)
    val currentOnOpenWeb by rememberUpdatedState(onOpenWeb)
    val currentOnShowDashboard by rememberUpdatedState(onShowDashboard)

    LaunchedEffect(events) {
        events.collect { event ->
            val message = when (event) {
                is DashboardEvent.Message -> event.text
                is DashboardEvent.MoreInfo -> {
                    currentOnMoreInfo(event.entityId)
                    null
                }
                is DashboardEvent.OpenAppLink -> {
                    // The app's own link handler, so the link never leaves the app
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri)).setPackage(context.packageName)
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Timber.w(e, "No activity handles the app link")
                    }
                    null
                }
                is DashboardEvent.OpenWeb -> {
                    currentOnOpenWeb(event.path)
                    null
                }
                DashboardEvent.ShowDashboard -> {
                    currentOnShowDashboard()
                    null
                }
                is DashboardEvent.UnsupportedAction ->
                    context.getString(R.string.native_dashboard_action_unsupported, event.type)
                is DashboardEvent.EnterCode -> {
                    codeFor = event.action
                    null
                }
                is DashboardEvent.Confirm -> {
                    confirm = event
                    null
                }
                is DashboardEvent.OpenUrl -> {
                    try {
                        uriHandler.openUri(event.url)
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e, "Cannot open the URL of a card action")
                    } catch (e: ActivityNotFoundException) {
                        Timber.w(e, "No app opens the URL of a card action")
                    }
                    null
                }
            }
            // Don't hold up later events while a snackbar is shown
            if (message != null) launch { snackbar.showSnackbar(message) }
        }
    }

    confirm?.let { pending ->
        val dismiss = { confirm = null }
        AlertDialog(
            onDismissRequest = dismiss,
            title = pending.confirmation.title?.let { { Text(it, style = HATextStyle.HeadlineMedium) } },
            text = { Text(pending.confirmation.text, style = HATextStyle.Body) },
            confirmButton = {
                HAPlainButton(pending.confirmation.confirmText ?: stringResource(commonR.string.ok), {
                    confirm = null
                    onConfirmed(pending.action)
                })
            },
            dismissButton = {
                HAPlainButton(pending.confirmation.dismissText ?: stringResource(commonR.string.cancel), dismiss)
            },
        )
    }

    codeFor?.let { action ->
        CodeDialog(
            request = checkNotNull(action.code),
            onSubmit = { code ->
                codeFor = null
                onCodeEntered(action, code)
            },
            onDismiss = { codeFor = null },
        )
    }
}
