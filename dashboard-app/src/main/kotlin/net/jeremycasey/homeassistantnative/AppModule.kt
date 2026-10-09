package net.jeremycasey.homeassistantnative

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.sensors.SensorSettingsIntentProvider
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.di.qualifiers.LocationTrackingSupport
import io.homeassistant.companion.android.di.qualifiers.WebViewCookies
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * What `:common` needs from the app. This app neither registers as a mobile device nor tracks location, so the
 * features that rely on those are off.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providesAppVersion(): AppVersion = AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.System

    @Provides
    @Singleton
    @PushWebsocketSupport
    fun providesPushWebsocketSupport(): Boolean = PUSH_WEBSOCKET_SUPPORT

    @Provides
    @Singleton
    @LocationTrackingSupport
    fun providesLocationTrackingSupport(): Boolean = LOCATION_TRACKING_SUPPORT

    /** No WebView in this app, so HTTP calls keep no WebView cookies and starting it doesn't load the WebView. */
    @Provides
    @Singleton
    @WebViewCookies
    fun providesWebViewCookies(): Boolean = WEBVIEW_COOKIES

    /** No push messaging, as the companion app's FOSS build, so no token. */
    @Provides
    @Singleton
    fun providesMessagingTokenProvider(): MessagingTokenProvider = MessagingTokenProvider { MessagingToken("") }

    /** This app has no sensors, so no sensor settings to link to. */
    @Provides
    @Singleton
    fun providesSensorSettingsIntentProvider(): SensorSettingsIntentProvider =
        SensorSettingsIntentProvider { _, _, _, _ -> null }
}

private const val PUSH_WEBSOCKET_SUPPORT = false
private const val LOCATION_TRACKING_SUPPORT = false
private const val WEBVIEW_COOKIES = false
