package io.homeassistant.companion.android.common.data.websocket

/**
 * Represents the state of a WebSocket connection
 */
sealed interface WebSocketState {

    data object Initial : WebSocketState
    data class Closed(val reason: Reason) : WebSocketState {
        enum class Reason {
            AUTH,
            CHANGED_URL,
            OTHER,
        }
    }

    data object Authenticating : WebSocketState

    data object Active : WebSocketState

    companion object {
        val ClosedAuth = Closed(Closed.Reason.AUTH)
        val ClosedUrlChange = Closed(Closed.Reason.CHANGED_URL)
        val ClosedOther = Closed(Closed.Reason.OTHER)
    }
}

/**
 * The state of a WebSocket connection, with how many connections have been established so far.
 *
 * @property connections grows by one every time a connection becomes [WebSocketState.Active], so a change tells
 * that the connection was re-established and that data read before may have changed since
 */
data class WebSocketConnectionStatus(val state: WebSocketState, val connections: Int) {
    /** This status moved to [newState], counting a new connection when it becomes active. */
    fun withState(newState: WebSocketState): WebSocketConnectionStatus = copy(
        state = newState,
        connections = if (newState == WebSocketState.Active &&
            state != WebSocketState.Active
        ) {
            connections + 1
        } else {
            connections
        },
    )
}
