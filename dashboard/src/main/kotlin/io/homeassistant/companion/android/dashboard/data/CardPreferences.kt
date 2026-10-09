package io.homeassistant.companion.android.dashboard.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.getSharedPreferencesSuspend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The choices made on cards that the frontend keeps in the browser's local storage, for every card and server
 * alike (such as the devices graph's chart type), kept on the device under the frontend's keys.
 */
@Singleton
class CardPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val storage: LocalStorage = LocalStorageImpl { context.getSharedPreferencesSuspend(PREFERENCES) }

    /** The values of [keys] that were set. */
    suspend fun load(keys: Collection<String>): Map<String, String> =
        keys.mapNotNull { key -> storage.getString(key)?.let { key to it } }.toMap()

    /** Remembers [value] for [key]. */
    suspend fun save(key: String, value: String) = storage.putString(key, value)

    private companion object {
        const val PREFERENCES = "dashboard_card_preferences"
    }
}
