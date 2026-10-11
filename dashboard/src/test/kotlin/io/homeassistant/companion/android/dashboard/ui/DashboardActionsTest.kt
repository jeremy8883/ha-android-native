package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardActionsTest {

    @Test
    fun `Given a navigate action when it runs then the dialogs close before the page changes`() = runTest {
        val events = Channel<DashboardEvent>(Channel.BUFFERED)
        val followed = mutableListOf<String>()
        val actions = DashboardActions(
            repository = mockk(),
            events = events,
            language = "en",
            localize = { error("not used") },
            navigate = { path ->
                // The close was sent first, so the details are gone when the page changes
                assertEquals(DashboardEvent.CloseDialogs, events.tryReceive().getOrNull())
                followed += path
            },
        )

        actions.run(CardAction.Navigate("/history?entity_id=sensor.backup&back=1", replace = false))

        assertEquals(listOf("/history?entity_id=sensor.backup&back=1"), followed)
        assertEquals(null, events.tryReceive().getOrNull())
    }
}
