package io.homeassistant.companion.android.dashboard.ui.panels.history

import app.cash.turbine.test
import io.homeassistant.companion.android.dashboard.data.CardPreferences
import io.homeassistant.companion.android.dashboard.data.HistoryRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.energy.StatisticValue
import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.history.HistoryState
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class HistoryPanelViewModelTest {

    private val sensor = "sensor.power"
    private val hass = HassSnapshot(
        states = emptyMap(),
        registries = Registries.EMPTY,
        user = null,
        config = HassConfig.UNKNOWN,
        panels = emptySet(),
        localize = { "" },
    )
    private val start = Instant.parse("2026-10-11T00:00:00Z")
    private val end = Instant.parse("2026-10-11T03:00:00Z")

    // A sensor that's gone, a number by its history's unit
    private val unit = JsonObject(mapOf("unit_of_measurement" to JsonPrimitive("W")))
    private val states = mapOf(sensor to listOf(HistoryState("5", unit, null, start.epochSecond + 600.0)))
    private val repository = mockk<HistoryRepository> {
        every { stream(any(), null, any()) } returns flowOf(Loadable.Ready(states))
        every { periodStatistics(any()) } returns flowOf(
            Loadable.Ready(mapOf(sensor to listOf(StatisticValue(start = 0, end = start.toEpochMilli(), mean = 4.0)))),
        )
    }

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) = StandardTestDispatcher(scheduler).let {
        HistoryPanelViewModel(repository, mockk<CardPreferences>(relaxed = true), mockk(relaxed = true), it, it)
    }

    @Test
    fun `Given nothing picked when showing the history then it asks to pick something without loading`() = runTest {
        val viewModel = viewModel(testScheduler)

        viewModel.history.test {
            viewModel.show(HistoryPanelRequest(emptyList(), start, end, hass))
            assertEquals(HistoryPanelState.Loading, awaitItem())
            assertEquals(HistoryPanelState.NoTarget, awaitItem())
        }
        verify(exactly = 0) { repository.stream(any(), any(), any()) }
    }

    @Test
    fun `Given a sensor when showing its history then its states come with the statistics from before them`() = runTest {
        val viewModel = viewModel(testScheduler)

        viewModel.history.test {
            viewModel.show(HistoryPanelRequest(listOf(sensor), start, end, hass))
            assertEquals(HistoryPanelState.Loading, awaitItem())
            var shown = awaitItem()
            // The states show first; the statistics fill in as they load
            while ((shown as HistoryPanelState.Ready).history.line.single().data.single().statistics == null) {
                shown = awaitItem()
            }
            val entity = shown.history.line.single().data.single()
            assertEquals(listOf("5"), entity.states.map { it.state })
            assertEquals(listOf("4"), entity.statistics!!.map { it.state })
        }
    }

    @Test
    fun `Given the stream fails when showing the history then it says so rather than nothing`() = runTest {
        every { repository.stream(any(), null, any()) } returns flowOf(Loadable.Failed(LoadError.NoResponse))
        val viewModel = viewModel(testScheduler)

        viewModel.history.test {
            viewModel.show(HistoryPanelRequest(listOf(sensor), start, end, hass))
            assertEquals(HistoryPanelState.Loading, awaitItem())
            assertEquals(HistoryPanelState.Failed(LoadError.NoResponse), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
