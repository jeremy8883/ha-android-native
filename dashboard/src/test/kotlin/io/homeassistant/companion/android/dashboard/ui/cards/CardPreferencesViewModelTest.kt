package io.homeassistant.companion.android.dashboard.ui.cards

import app.cash.turbine.test
import io.homeassistant.companion.android.dashboard.data.CardPreferences
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class CardPreferencesViewModelTest {

    private val preferences = mockk<CardPreferences> {
        coEvery { load(any()) } returns mapOf(DEVICES_CHART_TYPE_KEY to "pie")
        coEvery { save(any(), any()) } returns Unit
    }

    @Test
    fun `Given a kept chart type when loading the card preferences then it is there`() = runTest {
        val viewModel = CardPreferencesViewModel(preferences)

        viewModel.values.test {
            assertEquals(emptyMap<String, String>(), awaitItem())
            assertEquals(mapOf(DEVICES_CHART_TYPE_KEY to "pie"), awaitItem())
        }
    }

    @Test
    fun `Given the card preferences when changing the chart type then it is shown and kept`() = runTest {
        val viewModel = CardPreferencesViewModel(preferences)
        advanceUntilIdle()

        viewModel.set(DEVICES_CHART_TYPE_KEY, "bar")
        advanceUntilIdle()

        assertEquals(mapOf(DEVICES_CHART_TYPE_KEY to "bar"), viewModel.values.value)
        coVerify { preferences.save(DEVICES_CHART_TYPE_KEY, "bar") }
    }
}
