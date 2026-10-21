package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.data.LoadError
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DashboardStatusTest {

    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private val old = now - 10.minutes
    private val recent = now - 1.minutes

    @Test
    fun `Given live data when showing its age then it isn't shown`() {
        assertNull(DashboardStatus.CURRENT.shownAge(now))
    }

    @Test
    fun `Given cached data under five minutes old when showing its age then it isn't shown`() {
        val status = DashboardStatus(refreshing = false, offline = false, updatedAt = recent, refreshError = null)

        assertNull(status.shownAge(now))
    }

    @Test
    fun `Given cached data over five minutes old when showing its age then it is shown`() {
        val status = DashboardStatus(refreshing = false, offline = true, updatedAt = old, refreshError = null)

        assertEquals(old, status.shownAge(now))
    }

    @Test
    fun `Given old cached data being refreshed when showing its age then it is shown`() {
        val status = DashboardStatus(refreshing = true, offline = false, updatedAt = old, refreshError = null)

        assertEquals(old, status.shownAge(now))
    }

    @Test
    fun `Given recent cached data whose refresh failed when showing its age then it is shown`() {
        val status = DashboardStatus(
            refreshing = false,
            offline = false,
            updatedAt = recent,
            refreshError = null,
            refreshFailed = true,
        )

        assertEquals(recent, status.shownAge(now))
    }

    @Test
    fun `Given a refresh error when building the status then it counts as failed`() {
        val status = DashboardStatus(
            refreshing = false,
            offline = false,
            updatedAt = recent,
            refreshError = LoadError.NoResponse,
        )

        assertEquals(recent, status.shownAge(now))
    }
}
