package io.homeassistant.companion.android.dashboard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
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
 */
@Composable
internal fun DashboardEffects(
    events: Flow<DashboardEvent>,
    snackbar: SnackbarHostState,
    onConfirmed: (CardAction) -> Unit,
    onCodeEntered: (CardAction.CallService, String) -> Unit,
    onMoreInfo: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uriHandler = LocalUriHandler.current
    var confirm by remember { mutableStateOf<DashboardEvent.Confirm?>(null) }
    var codeFor by remember { mutableStateOf<CardAction.CallService?>(null) }
    // The events are collected once, so they reach the latest callbacks through this
    val navigation by rememberUpdatedState(NavigationCallbacks(onMoreInfo, onOpenWeb))

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is DashboardEvent.Confirm -> confirm = event
                is DashboardEvent.EnterCode -> codeFor = event.action
                else -> if (!navigate(event, uriHandler, navigation)) {
                    // Don't hold up later events while a snackbar is shown
                    messageFor(event, context, view)?.let { (text, duration) ->
                        launch { snackbar.showSnackbar(text, duration = duration) }
                    }
                }
            }
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

/** The frontend's `failure` haptic, as the app performs it for the web frontend (`HapticFeedbackPerformer`). */
private fun performFailureHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/** Where navigation events lead. */
private data class NavigationCallbacks(val onMoreInfo: (String) -> Unit, val onOpenWeb: (String) -> Unit)

/** Follow [event] when it navigates somewhere. @return whether it did */
private fun navigate(event: DashboardEvent, uriHandler: UriHandler, callbacks: NavigationCallbacks): Boolean {
    when (event) {
        is DashboardEvent.MoreInfo -> callbacks.onMoreInfo(event.entityId)
        is DashboardEvent.OpenWeb -> callbacks.onOpenWeb(event.path)
        is DashboardEvent.OpenUrl -> openUrl(uriHandler, event.url)
        else -> return false
    }
    return true
}

private fun openUrl(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Cannot open the URL of a card action")
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No app opens the URL of a card action")
    }
}

/** The message [event] shows, and for how long; `null` when it isn't a message. A failed action is felt too. */
private fun messageFor(event: DashboardEvent, context: Context, view: View): Pair<String, SnackbarDuration>? =
    when (event) {
        is DashboardEvent.Message -> event.text to SnackbarDuration.Short
        is DashboardEvent.ActionFailed -> {
            performFailureHaptic(view)
            // Upstream shows it for 10s, a long snackbar's duration
            event.text to SnackbarDuration.Long
        }
        is DashboardEvent.LoadFailed -> context.getString(
            R.string.native_dashboard_load_failed,
            context.loadErrorText(event.error),
        ) to SnackbarDuration.Short
        is DashboardEvent.UnsupportedAction ->
            context.getString(R.string.native_dashboard_action_unsupported, event.type) to SnackbarDuration.Short
        else -> null
    }
