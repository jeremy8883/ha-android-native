package io.homeassistant.companion.android.dashboard.data

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.dashboard.data.cache.CachedEntityState
import io.homeassistant.companion.android.dashboard.data.cache.CachedValue
import io.homeassistant.companion.android.dashboard.data.cache.DashboardCacheDao
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.entity.toCompressed
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * The last value loaded of each piece of server data, by server: in memory for the life of the app, and in the
 * dashboard cache so the dashboards show at once, even offline, the next time the app starts. A server's data is
 * forgotten when the server is removed from the app (which is how the app logs out).
 *
 * Writes are batched: at most one every [WRITE_DELAY] for each piece, with its latest value.
 */
@Singleton
class LoadedData @VisibleForTesting internal constructor(
    private val dao: DashboardCacheDao,
    private val clock: Clock,
    serverIds: Flow<Set<Int>>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(dao: DashboardCacheDao, clock: Clock, serverManager: ServerManager) : this(
        dao,
        clock,
        serverManager.serversFlow.map { servers -> servers.map { it.id }.toSet() },
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val memory = ConcurrentHashMap<Key, Kept<*>>()

    /** What is cached for each server, read once (see [cached]). */
    private val cache = ConcurrentHashMap<Int, Deferred<CachedServer>>()
    private val pendingWrites = ConcurrentHashMap.newKeySet<Key>()

    /** The states last written for each server, to write only what changed since. */
    private val writtenStates = ConcurrentHashMap<Int, EntityStates>()
    private val statesWrite = Mutex()

    init {
        scope.launch { serverIds.distinctUntilChanged().collect(::forgetRemovedServers) }
    }

    /**
     * Start reading [serverId]'s cache now, before anything asks for it, so that it is ready (or nearly) when the
     * dashboards do. Meant for when the app starts, while its screens are still being set up.
     */
    fun preload(serverId: Int) {
        cached(serverId)
    }

    /** Where [name] (for example `registries`) of [serverId] is kept, written to the cache with [codec]. */
    fun <T> keeper(serverId: Int, name: String, codec: CacheCodec<T>): ValueKeeper<T> = object : ValueKeeper<T> {
        private val key = Key(serverId, name)

        override suspend fun get(): Kept<T>? = memory(key) ?: read(key, codec)

        override fun put(value: T) {
            memory[key] = Kept(value, clock.now())
            scheduleWrite(key) { kept: Kept<T> ->
                dao.putValue(CachedValue(serverId, name, codec.encode(kept.value), kept.at.toEpochMilliseconds()))
            }
        }
    }

    /** Where the entity states of [serverId] are kept, written to the cache one entity at a time. */
    fun statesKeeper(serverId: Int): ValueKeeper<EntityStates> = object : ValueKeeper<EntityStates> {
        private val key = Key(serverId, STATES)

        override suspend fun get(): Kept<EntityStates>? =
            memory(key) ?: cached(serverId).await().states?.also { memory.putIfAbsent(key, it) }

        override fun put(value: EntityStates) {
            memory[key] = Kept(value, clock.now())
            scheduleWrite(key) { kept: Kept<EntityStates> -> writeStates(serverId, kept) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> memory(key: Key): Kept<T>? = memory[key] as Kept<T>?

    private suspend fun <T> read(key: Key, codec: CacheCodec<T>): Kept<T>? {
        val cached = cached(key.serverId).await().values[key.name] ?: return null
        return codec.decode(cached.json)
            ?.let {
                Kept(it, Instant.fromEpochMilliseconds(cached.savedAt)).also { kept -> memory.putIfAbsent(key, kept) }
            }
            ?: null.also { Timber.w("Ignoring cached ${key.name}, which can't be read") }
    }

    /**
     * What is cached for [serverId], read from the database once, all at once: everything cached is needed when the
     * dashboards start. A failed read is tried again the next time.
     */
    private fun cached(serverId: Int): Deferred<CachedServer> = cache.computeIfAbsent(serverId) {
        scope.async {
            try {
                val values = dao.values(serverId).associateBy { it.name }
                CachedServer(values, values[STATES]?.let { saved -> readStates(serverId, saved) })
            } catch (e: IllegalStateException) {
                // Room reports database failures this way
                cache.remove(serverId)
                throw e
            }
        }
    }

    private suspend fun readStates(serverId: Int, saved: CachedValue): Kept<EntityStates> {
        val rows = dao.states(serverId)
        val compressed = rows.mapNotNull { row ->
            decodeObject(row.json)?.let { row.entityId to it }
                ?: null.also { Timber.w("Ignoring the cached state of ${row.entityId}, which can't be read") }
        }.toMap()
        val states = applyEntityEvent(emptyMap(), JsonObject(mapOf("a" to JsonObject(compressed))))
        writtenStates[serverId] = states
        return Kept(states, Instant.fromEpochMilliseconds(saved.savedAt))
    }

    /** Write [key]'s latest value with [write] shortly, unless a write is already on its way. */
    private fun <T> scheduleWrite(key: Key, write: suspend (Kept<T>) -> Unit) {
        if (!pendingWrites.add(key)) return
        scope.launch {
            delay(WRITE_DELAY)
            pendingWrites.remove(key)
            val latest = memory<T>(key) ?: return@launch
            try {
                write(latest)
            } catch (e: IllegalStateException) {
                // Room reports database failures this way; the next change writes again
                Timber.e(e, "Failed to cache ${key.name} for server ${key.serverId}")
            }
        }
    }

    private suspend fun writeStates(serverId: Int, kept: Kept<EntityStates>) = statesWrite.withLock {
        val states = kept.value
        val saved = CachedValue(serverId, STATES, "{}", kept.at.toEpochMilliseconds())
        val written = writtenStates[serverId]
        if (written == null) {
            dao.replaceStates(serverId, states.values.map { it.toRow(serverId) }, saved)
        } else {
            // An unchanged entity keeps its instance (see applyEntityEvent), so a reference check finds the changes
            val changed = states.values.filter { written[it.entityId] !== it }.map { it.toRow(serverId) }
            val removed = written.keys.filter { it !in states }
            dao.updateStates(serverId, changed, removed, saved)
        }
        writtenStates[serverId] = states
    }

    private suspend fun forgetRemovedServers(serverIds: Set<Int>) {
        val removed = (dao.serverIds() + memory.keys.map { it.serverId }).toSet() - serverIds
        removed.forEach { serverId ->
            Timber.i("Forgetting the cached dashboard data of removed server $serverId")
            memory.keys.removeAll { it.serverId == serverId }
            cache.remove(serverId)?.cancel()
            writtenStates.remove(serverId)
            dao.deleteServer(serverId)
        }
    }

    private data class Key(val serverId: Int, val name: String)

    /** A server's cached values by name, and its cached states. */
    private class CachedServer(val values: Map<String, CachedValue>, val states: Kept<EntityStates>?)

    internal companion object {
        private const val STATES = "states"

        /** How long changes gather before they are written. */
        val WRITE_DELAY = 2.seconds
    }
}

private fun EntityState.toRow(serverId: Int) = CachedEntityState(serverId, entityId, toCompressed().toString())
