package io.homeassistant.companion.android.dashboard.ui.moreinfo

import app.cash.turbine.test
import io.homeassistant.companion.android.dashboard.data.EntityStatistics
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.energy.StatisticsMetadata
import io.homeassistant.companion.android.dashboard.history.HistoryState
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class MoreInfoHistoryViewModelTest {

    private val states = mapOf("sensor.power" to listOf(HistoryState("1", null, null, 1.0)))
    private val statistics = EntityStatistics(StatisticsMetadata("sensor.power", "W", "recorder", null, false, "power"), emptyList())
    private val repository = mockk<HistoryRepository> {
        every { stream(any(), any(), any()) } returns flowOf(Loadable.Ready(states))
    }

    private fun viewModel() = MoreInfoHistoryViewModel(repository, FakeClock())

    @Test
    fun `Given a sensor with statistics when showing its history then it shows the statistics`() = runTest {
        every { repository.statistics("sensor.power") } returns flowOf(Loadable.Ready(statistics))
        val viewModel = viewModel()

        viewModel.history.test {
            viewModel.show(HistoryRequest("sensor.power", statistics = true, withoutAttributes = true))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Ready(EntityHistory.Statistics(statistics)), awaitItem())
        }
        verify(exactly = 0) { repository.stream(any(), any(), any()) }
    }

    @Test
    fun `Given a sensor without statistics when its statistics load again then its states stream once`() = runTest {
        // Loaded again every minute: still none
        val polls = MutableStateFlow<Loadable<EntityStatistics?>>(Loadable.Ready(null))
        every { repository.statistics("sensor.power") } returns polls
        val viewModel = viewModel()

        viewModel.history.test {
            viewModel.show(HistoryRequest("sensor.power", statistics = true, withoutAttributes = true))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Ready(EntityHistory.States(states)), awaitItem())
            polls.value = Loadable.Ready(null, refreshing = true)
            expectNoEvents()
        }
        verify(exactly = 1) { repository.stream(any(), any(), any()) }
    }

    @Test
    fun `Given the statistics fail to load when showing the history then it says so rather than nothing`() = runTest {
        every { repository.statistics("sensor.power") } returns flowOf(Loadable.Failed(LoadError.NoResponse))
        val viewModel = viewModel()

        viewModel.history.test {
            viewModel.show(HistoryRequest("sensor.power", statistics = true, withoutAttributes = true))
            assertEquals(Loadable.Loading, awaitItem())
            assertEquals(Loadable.Failed(LoadError.NoResponse), awaitItem())
        }
    }
}
