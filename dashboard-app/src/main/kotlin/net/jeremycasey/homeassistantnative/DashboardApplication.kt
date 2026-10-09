package net.jeremycasey.homeassistantnative

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** The native dashboard app. */
@HiltAndroidApp
class DashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
