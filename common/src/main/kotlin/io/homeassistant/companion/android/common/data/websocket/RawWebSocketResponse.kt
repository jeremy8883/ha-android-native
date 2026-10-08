package io.homeassistant.companion.android.common.data.websocket

import kotlinx.serialization.json.JsonElement

/**
 * Undecoded response to a command sent with [WebSocketRepository.sendRawMessage].
 *
 * @property success whether the server reported the command as successful
 * @property result the `result` payload, when present
 * @property error the `error` payload (`{code, message}`), when present
 */
data class RawWebSocketResponse(val success: Boolean, val result: JsonElement?, val error: JsonElement?)
