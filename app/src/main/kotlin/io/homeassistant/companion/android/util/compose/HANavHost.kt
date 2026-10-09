package io.homeassistant.companion.android.util.compose

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import io.homeassistant.companion.android.automotive.navigation.carAppActivity
import io.homeassistant.companion.android.automotive.navigation.navigateToCarAppActivity
import io.homeassistant.companion.android.changelog.navigation.changelogScreen
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.frontend.navigation.FrontendCallbacks
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.nativedashboard.nativeDashboardScreen
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardApp
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.URL_SECURITY_LEVEL_DOCUMENTATION
import io.homeassistant.companion.android.onboarding.onboarding
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.navigateToSetHomeNetworkRoute
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.setHomeNetworkScreen
import io.homeassistant.companion.android.onboarding.wearOnboarding
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.navigation.navigateToSettings
import io.homeassistant.companion.android.settings.server.ServerChooserFragment

/**
 * Navigation host for the main application.
 *
 * This composable function sets up the navigation graph for the whole app.
 * The [NavHost] is only composed once [startDestination] is resolved; until then a bare
 * [LoadingScreen] is displayed instead.
 *
 * @param navController The [NavHostController] for managing navigation.
 * @param startDestination The initial destination of the navigation graph. If it is null [LoadingScreen]
 *                         is displayed.
 * @param onShowSnackbar A suspending function to display a snackbar.
 *                       It takes a [message] and an optional [action] label.
 *                       Returns `true` if the action was performed (if an action was provided),
 *                       `false` otherwise (e.g., dismissed).
 */
@Composable
internal fun HANavHost(
    navController: NavHostController,
    startDestination: HAStartDestinationRoute?,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onRequestFullscreen: (Boolean) -> Unit = {},
    onPipReadinessChanged: (PipReadiness?) -> Unit = {},
) {
    val activity = LocalActivity.current
    val isAutomotive = activity?.isAutomotive() == true

    startDestination?.let {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            onboarding(
                navController,
                onShowSnackbar = onShowSnackbar,
                onOnboardingDone = {
                    val navOptions = navOptions {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                    if (isAutomotive) {
                        navController.navigateToCarAppActivity(navOptions)
                    } else {
                        navController.navigateToFrontend(navOptions = navOptions)
                    }
                },
                urlToOnboard = (startDestination as? OnboardingRoute)?.urlToOnboard,
                hideExistingServers = (startDestination as? OnboardingRoute)?.hideExistingServers == true,
                skipWelcome = (startDestination as? OnboardingRoute)?.skipWelcome == true,
                hasLocationTracking = (startDestination as? OnboardingRoute)?.hasLocationTracking == true,
                fromInvitation = (startDestination as? OnboardingRoute)?.fromInvitation == true,
            )
            if (startDestination is WearOnboardingRoute) {
                wearOnboarding(
                    navController,
                    onOnboardingDone = {
                            deviceName: String,
                            serverUrl: String,
                            authCode: String,
                            certUri: Uri?,
                            certPassword: String?,
                        ->
                        activity?.setResult(
                            Activity.RESULT_OK,
                            WearOnboardApp.Output(
                                url = serverUrl,
                                authCode = authCode,
                                deviceName = deviceName,
                                tlsClientCertificateUri = certUri?.toString(),
                                tlsClientCertificatePassword = certPassword,
                            ).toIntent(),
                        )
                        activity?.finish()
                    },
                    onShowSnackbar = onShowSnackbar,
                    urlToOnboard = startDestination.urlToOnboard,
                    wearNameToOnboard = startDestination.wearName,
                )
            }
            // Named apart from the callbacks' own methods, which would otherwise call themselves
            val showSnackbar = onShowSnackbar
            val requestFullscreen = onRequestFullscreen
            val pipReadinessChanged = onPipReadinessChanged
            val frontendCallbacks = object : FrontendCallbacks {
                override suspend fun onOpenExternalLink(uri: Uri) =
                    navController.navigateToUri(uri.toString(), showSnackbar)

                override fun onNavigateToSettings(deeplink: SettingsActivity.Deeplink?) =
                    navController.navigateToSettings(deeplink)

                override suspend fun onSecurityLevelHelpClick() =
                    navController.navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, showSnackbar)

                override fun onOpenLocationSettings() {
                    activity?.let { openSystemLocationSettings(it) }
                }

                override fun onConfigureHomeNetwork(serverId: Int) =
                    navController.navigateToSetHomeNetworkRoute(serverId)

                override suspend fun onShowSnackbar(message: String, action: String?) = showSnackbar(message, action)

                override fun onShowServerSwitcher(onServerSelected: (Int) -> Unit) =
                    showServerSwitcher(activity, onServerSelected)

                override suspend fun onLaunchApp(packageName: String) =
                    navController.launchAppOrStore(packageName, showSnackbar)

                override suspend fun onLaunchIntent(intentUri: String) =
                    navController.launchIntentUri(intentUri, showSnackbar)

                override suspend fun onOpenSecuritySettings() = navController.openSecuritySettings(showSnackbar)

                override suspend fun onUpdateWebView() = navController.updateSystemWebView(showSnackbar)

                override fun onRequestFullscreen(fullscreen: Boolean) = requestFullscreen(fullscreen)

                override fun onPipReadinessChanged(readiness: PipReadiness?) = pipReadinessChanged(readiness)
            }
            nativeDashboardScreen(frontendCallbacks = frontendCallbacks)
            frontendScreen(navController = navController, callbacks = frontendCallbacks)
            changelogScreen(
                navController = navController,
                onOpenUrl = { url -> navController.navigateToUri(url, onShowSnackbar) },
            )
            setHomeNetworkScreen(
                onGotoNextScreen = {
                    navController.popBackStack<FrontendRoute>(inclusive = false)
                },
                onHelpClick = {
                    navController.navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, showSnackbar)
                },
            )

            if (isAutomotive) {
                carAppActivity(navController)
            }
        }
    } ?: LoadingScreen(showBrand = true)
}

/**
 * Opens the Android location settings screen.
 *
 * If location is already enabled, this is a no-op.
 * Falls back to general settings if location settings are not available.
 */
private fun openSystemLocationSettings(activity: Activity) {
    if (DisabledLocationHandler.isLocationEnabled(activity)) {
        return
    }
    activity.startActivity(DisabledLocationHandler.locationSettingsIntent(activity))
}

private fun showServerSwitcher(activity: Activity?, onServerSelected: (Int) -> Unit) {
    val fragmentActivity = activity as? FragmentActivity
    if (fragmentActivity == null) {
        FailFast.fail { "Cannot show server switcher: hosting activity is not a FragmentActivity" }
    } else {
        val fragmentManager = fragmentActivity.supportFragmentManager
        fragmentManager.setFragmentResultListener(
            ServerChooserFragment.RESULT_KEY,
            fragmentActivity,
        ) { _, bundle ->
            if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                onServerSelected(bundle.getInt(ServerChooserFragment.RESULT_SERVER))
            }
            fragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
        }
        ServerChooserFragment().show(fragmentManager, ServerChooserFragment.TAG)
    }
}
