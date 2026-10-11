package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction.Assist
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction.EditDashboard
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction.Search
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardMenuTest {
    private val conversation = setOf("conversation")

    @Test
    fun `Given a narrow screen when building the menu then everything is in the overflow`() {
        assertEquals(
            DashboardMenu(buttons = emptyList(), overflow = listOf(Search, Assist, EditDashboard)),
            dashboardMenu(narrow = true, isAdmin = true, recoveryMode = false, components = conversation),
        )
    }

    @Test
    fun `Given a wide screen and an admin when building the menu then Edit dashboard alone is promoted`() {
        assertEquals(
            DashboardMenu(buttons = listOf(Search, Assist, EditDashboard), overflow = emptyList()),
            dashboardMenu(narrow = false, isAdmin = true, recoveryMode = false, components = conversation),
        )
    }

    @Test
    fun `Given a user without conversation when building the menu then only Search`() {
        assertEquals(
            DashboardMenu(buttons = emptyList(), overflow = listOf(Search)),
            dashboardMenu(narrow = true, isAdmin = false, recoveryMode = false, components = emptySet()),
        )
        assertEquals(
            DashboardMenu(buttons = listOf(Search), overflow = emptyList()),
            dashboardMenu(narrow = false, isAdmin = true, recoveryMode = true, components = emptySet()),
        )
    }
}
