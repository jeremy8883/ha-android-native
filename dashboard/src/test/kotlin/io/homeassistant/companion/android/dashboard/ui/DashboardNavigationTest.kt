package io.homeassistant.companion.android.dashboard.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardNavigationTest {

    private fun navigation(stack: List<String>, returnPoints: List<Location> = emptyList()) = Navigation(stack, canOpenOtherPages = true, returnPoints = returnPoints, panelQuery = null)

    @Test
    fun `Given a subview when finding where back returns then it is the views under it and the default dashboard`() {
        val history = navigation(listOf("rooms", "kitchen")).history("dashboard-test")

        assertEquals(Location("dashboard-test", listOf("rooms", "kitchen")), history.current)
        assertEquals(
            setOf(
                Location("dashboard-test", emptyList()),
                Location("dashboard-test", listOf("rooms")),
                Location(null, emptyList()),
            ),
            history.reachable,
        )
    }

    @Test
    fun `Given a page opened from another when finding where back returns then the opener's views are included`() {
        val history = navigation(emptyList(), returnPoints = listOf(Location(null, listOf("maintenance"))))
            .history("history")

        assertEquals(
            setOf(Location(null, emptyList()), Location(null, listOf("maintenance"))),
            history.reachable,
        )
    }

    @Test
    fun `Given the default dashboard's first view when finding where back returns then nothing is left`() {
        assertEquals(emptySet<Location>(), navigation(emptyList()).history(null).reachable)
    }

    @Test
    fun `Given locations when keying them then each dashboard and view path has its own key`() {
        assertEquals("/maintenance", Location(null, listOf("maintenance")).key)
        assertEquals("dashboard-test/rooms/kitchen", Location("dashboard-test", listOf("rooms", "kitchen")).key)
    }
}
