package io.homeassistant.companion.android.frontend.navigation

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.os.bundleOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.frontend.FrontendScreen
import io.homeassistant.companion.android.frontend.FrontendViewModel
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget.Companion.toRawPath
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.nativedashboard.NativeDashboardHandOff
import io.homeassistant.companion.android.nativedashboard.navigateToNativeDashboard
import io.homeassistant.companion.android.nfc.WriteNfcTag
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.getActivity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

@Serializable
internal data class FrontendRoute
/**
 * The destination is stored as the raw [rawPath] string rather than a custom-typed field on
 * purpose: a custom `NavType` for a [FrontendTarget] field would force a `typeOf`/kotlin-reflect
 * lookup on the main thread during navigation.
 *
 * Annotated `@VisibleForTesting` so the linter discourages building a route from a raw path;
 * production code should use the [FrontendTarget] constructor instead. It remains used by the
 * generated kotlinx.serialization route serializer.
 */
@VisibleForTesting constructor(
    private val rawPath: String? = null,
    val serverId: Int = SERVER_ID_ACTIVE,
) : HAStartDestinationRoute {

    constructor(target: FrontendTarget, serverId: Int = SERVER_ID_ACTIVE) : this(target.toRawPath(), serverId)

    val target: FrontendTarget get() = FrontendTarget.fromRawPath(rawPath)

    /** The arguments navigating to this route gives, for a [FrontendViewModel] created outside its destination. */
    fun toArguments(): Bundle = bundleOf(
        *listOfNotNull(rawPath?.let { RAW_PATH_ARGUMENT to it }).toTypedArray(),
        SERVER_ID_ARGUMENT to serverId,
    )
}

// The property names of FrontendRoute, which the route serializer uses as argument keys
private const val RAW_PATH_ARGUMENT = "rawPath"
private const val SERVER_ID_ARGUMENT = "serverId"

internal fun NavController.navigateToFrontend(
    target: FrontendTarget = FrontendTarget.Default,
    serverId: Int = SERVER_ID_ACTIVE,
    navOptions: NavOptions? = null,
) {
    navigate(FrontendRoute(target, serverId), navOptions)
}

/**
 * What the frontend asks of the app, shared by every place hosting it.
 *
 * @param onOpenExternalLink Callback to open external links (required for V2)
 * @param onNavigateToSettings Callback to navigate to settings
 * @param onSecurityLevelHelpClick Callback when user taps help on security level screen
 * @param onOpenLocationSettings Callback to open location settings
 * @param onConfigureHomeNetwork Callback to configure home network (receives serverId)
 * @param onShowSnackbar Callback to show snackbar messages
 * @param onShowServerSwitcher Callback to display the server switcher bottom sheet. Receives an
 *   `onServerSelected` callback that must be invoked with the chosen server ID.
 * @param onLaunchApp Callback to launch an installed app (or its store page) by package name
 * @param onLaunchIntent Callback to launch an Android `intent:` URI
 * @param onOpenSecuritySettings Callback to open the OS security settings (client-certificate installation)
 * @param onUpdateWebView Callback to open the current WebView provider's update page
 */
internal class FrontendCallbacks(
    val onOpenExternalLink: suspend (Uri) -> Unit = {},
    val onNavigateToSettings: (SettingsActivity.Deeplink?) -> Unit,
    val onSecurityLevelHelpClick: suspend () -> Unit,
    val onOpenLocationSettings: () -> Unit,
    val onConfigureHomeNetwork: (serverId: Int) -> Unit,
    val onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    val onShowServerSwitcher: (onServerSelected: (Int) -> Unit) -> Unit,
    val onLaunchApp: suspend (packageName: String) -> Unit = {},
    val onLaunchIntent: suspend (intentUri: String) -> Unit = {},
    val onOpenSecuritySettings: suspend () -> Unit = {},
    val onUpdateWebView: suspend () -> Unit = {},
    val onRequestFullscreen: (Boolean) -> Unit = {},
    val onPipReadinessChanged: (PipReadiness?) -> Unit = {},
)

/**
 * Registers the frontend/webview destination for the Home Assistant app.
 *
 * @param navController The navigation controller
 * @param callbacks What the frontend asks of the app
 */
internal fun NavGraphBuilder.frontendScreen(navController: NavController, callbacks: FrontendCallbacks) {
    composable<FrontendRoute> {
        val viewModel: FrontendViewModel = hiltViewModel()
        if (WIPFeature.USE_NATIVE_DASHBOARD) {
            NativeDashboardHandOff(viewModel.frontendRoute) { path -> navController.navigateToNativeDashboard(path) }
        }
        FrontendContent(
            viewModel = viewModel,
            callbacks = callbacks,
            onShowServerSwitcher = { callbacks.onShowServerSwitcher(viewModel::switchServer) },
            onShowNativeNavigation = { navController.navigateToNativeDashboard(openDrawer = true) },
        )
    }
}

/**
 * The frontend screen of [viewModel] with its one-shot events handled, wherever it is hosted.
 *
 * @param onShowServerSwitcher shows the server picker, as the frontend asks
 * @param onShowNativeNavigation shows the app's own navigation, when the native dashboards are on
 */
@Composable
internal fun FrontendContent(
    viewModel: FrontendViewModel,
    callbacks: FrontendCallbacks,
    onShowServerSwitcher: () -> Unit,
    onShowNativeNavigation: () -> Unit,
) {
    val context = LocalContext.current
    val nfcWriteLauncher = rememberLauncherForActivityResult(WriteNfcTag()) { messageId ->
        viewModel.onNfcWriteCompleted(messageId)
    }
    val matterThreadIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onMatterThreadIntentResult(result) }

    FrontendEventHandler(
        events = viewModel.events,
        onShowNativeNavigation = onShowNativeNavigation,
        onShowSnackbar = callbacks.onShowSnackbar,
        onNavigateToSettings = callbacks.onNavigateToSettings,
        onRelaunch = {
            // Clear the task so the relaunch starts from scratch and back can't return to
            // the pre-relaunch state (e.g. after removing the server or clearing credentials).
            context.startActivity(
                LaunchActivity.newInstance(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            context.getActivity()?.finish()
        },
        onNavigateToAssist = { serverId, pipelineId, startListening ->
            context.startActivity(
                AssistActivity.newInstance(
                    context = context,
                    serverId = serverId,
                    pipelineId = pipelineId,
                    startListening = startListening,
                ),
            )
        },
        onOpenExternalLink = callbacks.onOpenExternalLink,
        onShowServerSwitcher = onShowServerSwitcher,
        onNavigateToNfcWrite = { messageId, tagId ->
            nfcWriteLauncher.launch(WriteNfcTag.Input(tagId = tagId, messageId = messageId))
        },
        onLaunchMatterThreadIntent = { intentSender ->
            matterThreadIntentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        },
        onRequestFullscreen = callbacks.onRequestFullscreen,
        onNavigateToWidgetConfig = { entityId, widgetType ->
            context.startActivity(widgetType.toConfigureIntent(context, entityId))
        },
        onLaunchApp = callbacks.onLaunchApp,
        onLaunchIntent = callbacks.onLaunchIntent,
        onOpenSecuritySettings = callbacks.onOpenSecuritySettings,
        onUpdateWebView = callbacks.onUpdateWebView,
    )

    FrontendScreen(
        viewModel = viewModel,
        onOpenExternalLink = callbacks.onOpenExternalLink,
        onBlockInsecureHelpClick = callbacks.onSecurityLevelHelpClick,
        onOpenSettings = { callbacks.onNavigateToSettings(null) },
        onOpenLocationSettings = callbacks.onOpenLocationSettings,
        onConfigureHomeNetwork = callbacks.onConfigureHomeNetwork,
        onSecurityLevelHelpClick = callbacks.onSecurityLevelHelpClick,
        onShowSnackbar = callbacks.onShowSnackbar,
        onPipReadinessChanged = callbacks.onPipReadinessChanged,
    )
}

/**
 * Handles one-shot events from the [FrontendViewModel].
 *
 * Collects [FrontendEvent]s and performs the appropriate action
 */
@Composable
@VisibleForTesting
internal fun FrontendEventHandler(
    events: SharedFlow<FrontendEvent>,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onNavigateToSettings: (SettingsActivity.Deeplink?) -> Unit,
    onNavigateToAssist: (serverId: Int, pipelineId: String?, startListening: Boolean) -> Unit,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onShowServerSwitcher: () -> Unit,
    onNavigateToNfcWrite: (messageId: Int, tagId: String?) -> Unit,
    onLaunchMatterThreadIntent: (IntentSender) -> Unit,
    onRequestFullscreen: (Boolean) -> Unit,
    onNavigateToWidgetConfig: (entityId: String, widgetType: WidgetType) -> Unit,
    onLaunchApp: suspend (packageName: String) -> Unit = {},
    onLaunchIntent: suspend (intentUri: String) -> Unit = {},
    onOpenSecuritySettings: suspend () -> Unit = {},
    onUpdateWebView: suspend () -> Unit = {},
    onRelaunch: () -> Unit = {},
    onShowNativeNavigation: () -> Unit = {},
) {
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        // Local suspend dispatcher so the snackbar's action can route its inner event back
        // through the same routing table (e.g. tapping "Get help" fires an OpenExternalLink).
        suspend fun handle(event: FrontendEvent) {
            when (event) {
                is FrontendEvent.ShowSnackbar -> {
                    val action = event.action
                    val tapped = onShowSnackbar(
                        event.resolveMessage(resources),
                        action?.let { resources.getString(it.labelResId) },
                    )
                    if (tapped && action != null) handle(action.event)
                }

                is FrontendEvent.NavigateToSettings -> onNavigateToSettings(null)
                is FrontendEvent.ShowNativeNavigation -> onShowNativeNavigation()
                is FrontendEvent.OpenSecuritySettings -> onOpenSecuritySettings()
                is FrontendEvent.UpdateWebView -> onUpdateWebView()
                is FrontendEvent.Relaunch -> onRelaunch()
                is FrontendEvent.NavigateToAssistSettings -> onNavigateToSettings(
                    SettingsActivity.Deeplink.AssistSettings,
                )

                is FrontendEvent.NavigateToAssist ->
                    onNavigateToAssist(event.serverId, event.pipelineId, event.startListening)

                is FrontendEvent.OpenExternalLink -> onOpenExternalLink(event.uri)
                is FrontendEvent.NavigateToDeveloperSettings -> onNavigateToSettings(
                    SettingsActivity.Deeplink.Developer,
                )

                is FrontendEvent.ShowServerSwitcher -> onShowServerSwitcher()
                is FrontendEvent.NavigateToNfcWrite -> onNavigateToNfcWrite(event.messageId, event.tagId)
                is FrontendEvent.LaunchMatterThreadIntent -> onLaunchMatterThreadIntent(event.intentSender)
                is FrontendEvent.RequestFullscreen -> onRequestFullscreen(event.fullscreen)
                is FrontendEvent.LaunchApp -> onLaunchApp(event.packageName)
                is FrontendEvent.LaunchIntent -> onLaunchIntent(event.intentUri)
                is FrontendEvent.NavigateToWidgetConfig -> onNavigateToWidgetConfig(event.entityId, event.widgetType)
            }
        }
        events.collect { event -> handle(event) }
    }
}
