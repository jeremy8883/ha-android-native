package io.homeassistant.companion.android.frontend.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FrontendRouteTest {

    @Test
    fun `Given a route to a path when reading its arguments as a route then it is the same route`() {
        assertRoundTrip(FrontendRoute(FrontendTarget.Path("/config/areas"), serverId = 3))
    }

    @Test
    fun `Given a route to the default panel when reading its arguments as a route then it is the same route`() {
        assertRoundTrip(FrontendRoute(FrontendTarget.Default, SERVER_ID_ACTIVE))
    }

    private fun assertRoundTrip(route: FrontendRoute) {
        val arguments = route.toArguments()
        val handle = SavedStateHandle(arguments.keySet().associateWith { arguments.get(it) })
        val read = handle.toRoute<FrontendRoute>()
        assertEquals(route.target, read.target)
        assertEquals(route.serverId, read.serverId)
    }
}
