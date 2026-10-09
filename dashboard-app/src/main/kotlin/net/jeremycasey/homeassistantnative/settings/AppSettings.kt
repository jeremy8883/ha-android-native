package net.jeremycasey.homeassistantnative.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.getSharedPreferencesSuspend
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

/** The app's own settings, kept on the device. */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val storage: LocalStorage = LocalStorageImpl { context.getSharedPreferencesSuspend(PREFERENCES) }
    private val otherPages = MutableStateFlow<Boolean?>(null)

    /**
     * Whether pages the native dashboards don't show (History, Map, an entity's full details...) open in the
     * Home Assistant companion app, or the browser without it. Off by default, so the app never sends you elsewhere
     * without asking.
     */
    val openOtherPagesInCompanionApp: Flow<Boolean> = otherPages
        .onStart { if (otherPages.value == null) otherPages.value = storage.getBoolean(OTHER_PAGES) }
        .filterNotNull()

    suspend fun setOpenOtherPagesInCompanionApp(open: Boolean) {
        storage.putBoolean(OTHER_PAGES, open)
        otherPages.value = open
    }

    private companion object {
        const val PREFERENCES = "app_settings"
        const val OTHER_PAGES = "open_other_pages_in_companion_app"
    }
}
