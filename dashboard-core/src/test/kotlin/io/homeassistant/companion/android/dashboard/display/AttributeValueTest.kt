package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttributeValueTest {
    private val hass = GoldenFixture("test-instance").hass
    private val hvac = hass.states.getValue("climate.hvac")

    @Test
    fun `Given a thermostat's modes when showing them then each is the translated state`() {
        assertEquals("Off, Heat, Cool, Auto, Dry, Fan only", hass.attributeValueDisplay(hvac, "hvac_modes"))
    }

    @Test
    fun `Given a thermostat's fan modes when showing them then each is the fan mode's translation`() {
        assertEquals(
            "On low, On high, Auto low, Auto high, Off",
            hass.attributeValueDisplay(hvac, "fan_modes"),
        )
    }

    @Test
    fun `Given a list without a value attribute when showing it then each value is formatted on its own`() {
        val state = hvac.copy(attributes = json("""{"other": ["on_low", "b"]}"""))
        assertEquals("on_low, b", hass.attributeValueDisplay(state, "other"))
    }

    @Test
    fun `Given an object when showing it then it is js-yaml's dump with single quotes`() {
        val state = hvac.copy(attributes = json("""{"data": {"a": 1, "b": ["x", "yes"], "c": "on"}}"""))
        assertEquals("a: 1\nb:\n  - x\n  - 'yes'\nc: 'on'", hass.attributeValueDisplay(state, "data"))
    }

    @Test
    fun `Given a temperature when hiding the unit then only the number shows`() {
        assertEquals("7", hass.attributeValueDisplay(hvac, "min_temp", hideUnit = true))
        assertEquals("7 °C", hass.attributeValueDisplay(hvac, "min_temp"))
    }

    @Test
    fun `Given a water heater's operation list when finding its value attribute then it is the operation mode`() {
        assertEquals("operation_mode", valueAttribute("water_heater", "operation_list"))
        assertEquals(MAIN_STATE, valueAttribute("climate", "hvac_modes"))
    }
}
