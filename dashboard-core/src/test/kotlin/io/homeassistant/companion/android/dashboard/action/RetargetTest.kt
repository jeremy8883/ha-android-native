package io.homeassistant.companion.android.dashboard.action

import io.homeassistant.companion.android.dashboard.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetargetTest {
    @Test
    fun `Given a stand-in's call when sending it to the group then only its entity changes`() {
        val call = CardAction.CallService("light", "turn_on", json("""{"entity_id": "light.__group_x", "brightness": 10}"""), target = null)
        val sent = call.retargeted("light.__group_x", "group.x") as CardAction.CallService
        assertEquals(json("""{"entity_id": "group.x", "brightness": 10}"""), sent.data)
        val listed = call.copy(data = json("""{"entity_id": ["light.__group_x", "light.other"]}"""))
        assertEquals(json("""{"entity_id": ["group.x", "light.other"]}"""), (listed.retargeted("light.__group_x", "group.x") as CardAction.CallService).data)
    }
}
