package net.jeremycasey.homeassistantnative

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.asNetworkClient
import dagger.hilt.android.HiltAndroidApp
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.dashboard.data.LoadedData
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber

/** The native dashboard app. */
@HiltAndroidApp
class DashboardApplication : Application() {

    @Inject
    lateinit var okHttpClientProvider: SuspendProvider<OkHttpClient>

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var loadedData: LoadedData

    override fun onCreate() {
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // As the companion app (HomeAssistantApplication): images load through the app's HTTP client (its TLS and
        // client certificates), registered before anything can ask Coil for its default loader
        val networkClient = CompletableDeferred<NetworkClient>()
        initializeCoil { networkClient.await() }
        super.onCreate()
        MainScope().launch { networkClient.complete(okHttpClientProvider().asNetworkClient()) }
        // The dashboards' cache is read while the screen is being set up, so they show at once
        MainScope().launch { serverManager.getServer()?.id?.let(loadedData::preload) }
    }
}

@OptIn(ExperimentalCoilApi::class)
private fun Context.initializeCoil(networkClient: suspend () -> NetworkClient) {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(this)
            .components { add(NetworkFetcher.Factory(networkClient = { deferredNetworkClient { networkClient() } })) }
            .build()
    }
}

private fun deferredNetworkClient(client: suspend () -> NetworkClient): NetworkClient = object : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T = client().executeRequest(request, block)
}
