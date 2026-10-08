package io.homeassistant.companion.android.dashboard.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last value loaded of each piece of server data, by server, kept for the life of the app so that the dashboards
 * shown again (after the app was in the background, or on another dashboard) start from it rather than from nothing.
 */
@Singleton
class LoadedData @Inject constructor() {
    private val values = ConcurrentHashMap<Key, Kept<*>>()

    /** Where [name] (for example `registries`) of [serverId] is kept. */
    fun <T> keeper(serverId: Int, name: String): ValueKeeper<T> = object : ValueKeeper<T> {
        private val key = Key(serverId, name)

        @Suppress("UNCHECKED_CAST")
        override fun get(): Kept<T>? = values[key] as Kept<T>?

        override fun put(value: T) {
            values[key] = Kept(value)
        }
    }

    private data class Key(val serverId: Int, val name: String)
}
