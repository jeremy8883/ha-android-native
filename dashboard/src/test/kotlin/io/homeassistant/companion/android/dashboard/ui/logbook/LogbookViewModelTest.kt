package io.homeassistant.companion.android.dashboard.ui.logbook

import app.cash.turbine.test
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.LogbookRepository
import io.homeassistant.companion.android.dashboard.logbook.LogbookEntry
import io.homeassistant.companion.android.dashboard.logbook.TraceContext
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class LogbookViewModelTest {

    private val entries = listOf(LogbookEntry(whenSeconds = 1.0, entityId = "lock.door", state = "locked"))
    private val users = JsonArray(emptyList())
    private val traces = mapOf("context" to TraceContext("automation", "lock_up", "run"))
    private val repository = mockk<LogbookRepository> {
        every { entries("lock.door") } returns flowOf(Loadable.Ready(entries))
        every { users() } returns flowOf(Loadable.Ready(users))
        every { traceContexts() } returns flowOf(Loadable.Ready(traces))
    }

    @Test
    fun `Given an admin when showing a logbook then it has the users and traces`() = runTest {
        val viewModel = LogbookViewModel(repository)

        viewModel.logbook.test {
            viewModel.show(LogbookRequest(LogbookSource.Entity("lock.door"), isAdmin = true))
            assertEquals(Loadable.Loading, awaitItem())
            // The entries show first; the users and traces fill in as they load
            val complete = Loadable.Ready(LoadedLogbook(entries, users, traces))
            var shown = awaitItem()
            while (shown != complete) {
                assertEquals(entries, (shown as Loadable.Ready).value.entries)
                shown = awaitItem()
            }
        }
    }

    @Test
    fun `Given another user when showing a logbook then only the entries are loaded`() = runTest {
        val viewModel = LogbookViewModel(repository)

        viewModel.logbook.test {
            viewModel.show(LogbookRequest(LogbookSource.Entity("lock.door"), isAdmin = false))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Ready(LoadedLogbook(entries, null, null)), awaitItem())
        }
        verify(exactly = 0) { repository.users() }
        verify(exactly = 0) { repository.traceContexts() }
    }

    @Test
    fun `Given the entries fail to load when showing the logbook then it says so rather than nothing`() = runTest {
        every { repository.entries("lock.door") } returns flowOf(Loadable.Failed(LoadError.NoResponse))
        val viewModel = LogbookViewModel(repository)

        viewModel.logbook.test {
            viewModel.show(LogbookRequest(LogbookSource.Entity("lock.door"), isAdmin = true))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(LoadError.NoResponse), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given the Logbook panel's period when showing it then those entries are loaded`() = runTest {
        val start = Instant.parse("2026-10-11T00:00:00Z")
        val end = Instant.parse("2026-10-11T03:00:00Z")
        every { repository.entries(start, end, null) } returns flowOf(Loadable.Ready(entries))
        val viewModel = LogbookViewModel(repository)

        viewModel.logbook.test {
            viewModel.show(LogbookRequest(LogbookSource.Range(start, end, null), isAdmin = false))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Ready(LoadedLogbook(entries, null, null)), awaitItem())
        }
    }
}
