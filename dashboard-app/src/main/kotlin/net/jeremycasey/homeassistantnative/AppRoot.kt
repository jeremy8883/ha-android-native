package net.jeremycasey.homeassistantnative

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.NativeDashboard
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.jeremycasey.homeassistantnative.login.LoginScreen
import net.jeremycasey.homeassistantnative.settings.ServerSwitcherSheet
import net.jeremycasey.homeassistantnative.settings.SettingsScreen
import timber.log.Timber

@Serializable
private data object DashboardRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object AddServerRoute

/**
 * The app: the login until a server is logged in to, then the dashboards with the app's settings.
 *
 * Upstream: none (native: the app's root)
 */
@Composable
internal fun AppRoot() {
    val viewModel: AppViewModel = hiltViewModel()
    val hasServer by viewModel.hasServer.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(LocalHAColorScheme.current.colorSurfaceDefault)) {
        when (hasServer) {
            // The servers are read from the database, which is quick
            null -> Unit
            false -> {
                ReportDrawn()
                LoginScreen(onLoggedIn = {})
            }
            true -> LoggedIn(viewModel)
        }
    }
}

/**
 * The screens once logged in: the dashboards, the settings and adding a server.
 *
 * Upstream: none (native: the app's navigation)
 */
@Composable
private fun LoggedIn(viewModel: AppViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = DashboardRoute) {
        composable<DashboardRoute> {
            Dashboard(viewModel, onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAddServer = { navController.navigate(AddServerRoute) },
            )
        }
        composable<AddServerRoute> {
            LoginScreen(
                onLoggedIn = { navController.popBackStack(DashboardRoute, inclusive = false) },
                onClose = { navController.popBackStack() },
            )
        }
    }
}

/**
 * The native dashboard, with the server switcher and web pages it opens.
 *
 * Upstream: none (native: hosts the native dashboard and the server switcher)
 */
@Composable
private fun Dashboard(viewModel: AppViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = remember { NativeDashboardRequest() }
    var chooseServer by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    val openWeb: (String) -> Unit = { path ->
        scope.launch { openWebPage(context, path, viewModel.webUrl(path)) }
    }
    NativeDashboard(
        request = request,
        onOpenWeb = openWeb,
        onShowServerSwitcher = { onSelected -> chooseServer = onSelected },
        onOpenSettings = onOpenSettings,
    )
    chooseServer?.let { onSelected ->
        ServerSwitcherSheet(
            onSelected = { serverId ->
                chooseServer = null
                onSelected(serverId)
            },
            onDismiss = { chooseServer = null },
        )
    }
}

/**
 * Open a page the native dashboards don't show: in the companion app when it's installed (its
 * `homeassistant://navigate` link, on its own active server), otherwise at [webUrl] in the browser. A toast says so
 * first, and stays on screen over the other app.
 */
private fun openWebPage(context: Context, path: String, webUrl: Uri?) {
    val companionLink = Uri.parse("$COMPANION_NAVIGATE/${path.removePrefix("/")}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, companionLink))
        toast(context, R.string.unsupported_opening_companion)
        return
    } catch (e: ActivityNotFoundException) {
        Timber.d(e, "No companion app to open $path in, using the browser")
    }
    if (webUrl == null) {
        Timber.w("No safe address to open $path at")
        toast(context, R.string.unsupported_no_address)
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, webUrl))
        toast(context, R.string.unsupported_opening_browser)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No browser to open $path in")
        toast(context, R.string.unsupported_no_app)
    }
}

/** A toast rather than a snackbar: it stays on screen while another app opens. */
private fun toast(context: Context, message: Int) {
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
}

private const val COMPANION_NAVIGATE = "homeassistant://navigate"
