package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.states
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlarmPanelCardModelTest {
    private val card = CardConfig(json("""{"type": "alarm-panel", "entity": "alarm_control_panel.a"}"""))

    private fun panel(state: String, attributes: String = """{"code_format": "number", "supported_features": 3}""") = hass(states("""{"alarm_control_panel.a": {"s": "$state", "a": $attributes}}"""))

    @Test
    fun `Given a triggered panel when showing it then it pulses and only disarms`() {
        val model = panel("triggered").alarmPanelCardModel(card) as AlarmPanelCardModel.Shown
        assertTrue(model.pulsing)
        assertEquals(listOf("alarm_disarm" to true), model.actions.map { it.service to it.disarm })
    }

    @Test
    fun `Given a code when arming then it is sent, and an empty one is left out`() {
        val action = (panel("disarmed").alarmPanelCardModel(card) as AlarmPanelCardModel.Shown).actions.first()
        assertEquals(JsonPrimitive("1234"), action.call("alarm_control_panel.a", "1234").data?.get("code"))
        assertNull(action.call("alarm_control_panel.a", "").data?.get("code"))
    }

    @Test
    fun `Given a panel with a default code when showing it then no code is asked for`() {
        val hass = panel("disarmed").copy(alarmDefaultCodes = mapOf("alarm_control_panel.a" to true))
        assertNull((hass.alarmPanelCardModel(card) as AlarmPanelCardModel.Shown).code)
    }
}
