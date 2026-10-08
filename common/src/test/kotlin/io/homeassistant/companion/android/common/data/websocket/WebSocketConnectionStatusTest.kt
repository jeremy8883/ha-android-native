package io.homeassistant.companion.android.common.data.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSocketConnectionStatusTest {

    @Test
    fun `Given a connection lost and established again when following its status then each connection is counted once`() {
        val status = WebSocketConnectionStatus(WebSocketState.Initial, connections = 0)
            .withState(WebSocketState.Authenticating)
            .withState(WebSocketState.Active)
            .withState(WebSocketState.Active)
            .withState(WebSocketState.ClosedOther)
            .withState(WebSocketState.Authenticating)
            .withState(WebSocketState.Active)

        assertEquals(WebSocketConnectionStatus(WebSocketState.Active, connections = 2), status)
    }
}
