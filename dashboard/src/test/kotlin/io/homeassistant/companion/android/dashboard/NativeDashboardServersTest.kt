package io.homeassistant.companion.android.dashboard

import app.cash.turbine.test
import io.homeassistant.companion.android.dashboard.data.ActiveServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativeDashboardServersTest {

    @Test
    fun `Given another server activated when following the active server then it is the newly active one`() = runTest {
        var active = 1
        val repository = mockk<ActiveServerRepository> {
            every { activeServer() } returns flowOf(1 to 2)
            coEvery { activeServerId() } answers { active }
            coEvery { activateServer(any()) } answers { active = firstArg() }
        }
        val paths = mockk<NativeDashboardPaths>(relaxed = true)
        val servers = NativeDashboardServers(repository, paths)

        servers.activeServer.test {
            assertEquals(NativeDashboardServers.ActiveServer(serverId = 1, serverCount = 2), awaitItem())
            servers.activate(2)
            assertEquals(NativeDashboardServers.ActiveServer(serverId = 2, serverCount = 2), awaitItem())
        }
        coVerify { paths.reset() }
    }
}
