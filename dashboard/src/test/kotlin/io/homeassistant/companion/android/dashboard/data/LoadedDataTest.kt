package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.data.cache.CachedEntityState
import io.homeassistant.companion.android.dashboard.data.cache.CachedValue
import io.homeassistant.companion.android.dashboard.data.cache.DashboardCacheDao
import io.homeassistant.companion.android.dashboard.entity.EntityState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoadedDataTest {

    /** The cache's tables in memory. */
    private class FakeDao : DashboardCacheDao {
        val values = mutableMapOf<Pair<Int, String>, CachedValue>()
        val states = mutableMapOf<Pair<Int, String>, CachedEntityState>()
        var stateWrites = 0

        override suspend fun value(serverId: Int, name: String) = values[serverId to name]

        override suspend fun putValue(value: CachedValue) {
            values[value.serverId to value.name] = value
        }

        override suspend fun states(serverId: Int) = states.values.filter { it.serverId == serverId }

        override suspend fun putStates(states: List<CachedEntityState>) {
            stateWrites += states.size
            states.forEach { this.states[it.serverId to it.entityId] = it }
        }

        override suspend fun deleteStates(serverId: Int, entityIds: List<String>) {
            entityIds.forEach { states.remove(serverId to it) }
        }

        override suspend fun deleteAllStates(serverId: Int) {
            states.keys.removeAll { it.first == serverId }
        }

        override suspend fun serverIds() = values.keys.map { it.first }.distinct()

        override suspend fun deleteValues(serverId: Int) {
            values.keys.removeAll { it.first == serverId }
        }
    }

    private val clock = object : Clock {
        override fun now() = NOW
    }
    private val dao = FakeDao()
    private val servers = MutableStateFlow(setOf(SERVER))

    private fun TestScope.loadedData() = LoadedData(dao, clock, servers, backgroundScope)

    private object TextCodec : CacheCodec<String> {
        override fun encode(value: String) = "\"$value\""

        override fun decode(json: String) = json.removeSurrounding("\"").takeIf { it.isNotEmpty() }
    }

    private fun state(entityId: String, state: String) = EntityState(entityId, state, JsonObject(emptyMap()), contextId = null, lastChanged = 1.0, lastUpdated = 1.0)

    @Test
    fun `Given a value kept when the app starts again then it is read back from the cache with when it was kept`() = runTest {
        loadedData().keeper(SERVER, "registries", TextCodec).put("areas")
        advanceTimeBy(LoadedData.WRITE_DELAY * 2)

        val kept = loadedData().keeper(SERVER, "registries", TextCodec).get()

        assertEquals(Kept("areas", NOW), kept)
    }

    @Test
    fun `Given a cached value that can't be read when getting it then there is none`() = runTest {
        dao.putValue(CachedValue(SERVER, "registries", "\"\"", savedAt = 0))

        assertNull(loadedData().keeper(SERVER, "registries", TextCodec).get())
    }

    @Test
    fun `Given states kept again when written then only the changed and removed entities are written`() = runTest {
        val loadedData = loadedData()
        val keeper = loadedData.statesKeeper(SERVER)
        val kitchen = state("light.kitchen", "on")
        keeper.put(mapOf("light.kitchen" to kitchen, "light.hall" to state("light.hall", "on")))
        advanceTimeBy(LoadedData.WRITE_DELAY * 2)
        keeper.put(mapOf("light.kitchen" to kitchen, "light.porch" to state("light.porch", "off")))
        advanceTimeBy(LoadedData.WRITE_DELAY * 2)

        // The two first entities, then only the new one
        assertEquals(3, dao.stateWrites)
        val readBack = loadedData().statesKeeper(SERVER).get()?.value
        // Read by a new instance, without what's in memory
        assertEquals(setOf("light.kitchen", "light.porch"), readBack?.keys)
        assertEquals("off", readBack?.get("light.porch")?.state)
    }

    @Test
    fun `Given a server removed from the app then its cached data is forgotten`() = runTest {
        val loadedData = loadedData()
        loadedData.keeper(SERVER, "registries", TextCodec).put("areas")
        loadedData.statesKeeper(SERVER).put(mapOf("light.kitchen" to state("light.kitchen", "on")))
        advanceTimeBy(LoadedData.WRITE_DELAY * 2)

        servers.value = emptySet()
        runCurrent()

        assertEquals(emptyMap<Pair<Int, String>, CachedValue>(), dao.values)
        assertEquals(emptyMap<Pair<Int, String>, CachedEntityState>(), dao.states)
        assertNull(loadedData.keeper(SERVER, "registries", TextCodec).get())
    }

    private companion object {
        const val SERVER = 1
        val NOW = Instant.fromEpochSeconds(1_700_000_000)
    }
}
