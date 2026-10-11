package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.moreinfo.BigNumber
import io.homeassistant.companion.android.dashboard.moreinfo.CircularPrimary
import io.homeassistant.companion.android.dashboard.moreinfo.CircularSecondary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThermostatCardModelTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun thermostat(config: String) = hass.thermostatCardModel(CardConfig(json(config))) as ThermostatCardModel.Shown

    @Test
    fun `Given show_current_as_primary when deriving a thermostat then the current temperature is the big number and the target goes under it`() {
        val current = hass.states.getValue("climate.heatpump").attributes["current_temperature"].toString().toDouble()
        val model = thermostat("""{"type": "thermostat", "entity": "climate.heatpump", "show_current_as_primary": true}""")
        assertEquals(CircularPrimary.Current(BigNumber(current, "°C", 0)), model.control.primary)
        assertEquals(CircularSecondary.Targets(BigNumber(0.0, "°C", 1), range = false), model.secondary)
    }

    @Test
    fun `Given show_current_as_primary on a range thermostat when deriving it then the range goes under the current temperature`() {
        val model = thermostat("""{"type": "thermostat", "entity": "climate.ecobee", "show_current_as_primary": true}""")
        assertTrue(model.control.primary is CircularPrimary.Current)
        assertEquals(true, (model.secondary as CircularSecondary.Targets).range)
    }

    @Test
    fun `Given a thermostat by default when deriving it then the target is the big number and the current temperature under it`() {
        val model = thermostat("""{"type": "thermostat", "entity": "climate.heatpump"}""")
        assertTrue(model.secondary is CircularSecondary.Current)
        assertTrue(model.features.isEmpty())
    }

    @Test
    fun `Given features when deriving a thermostat then the supported ones are kept in the entity's colour`() {
        val model = thermostat(
            """{"type": "thermostat", "entity": "climate.heatpump",
            "features": [{"type": "target-temperature"}, {"type": "light-brightness"}]}""",
        )
        assertTrue(model.features.single() is TileFeature.NumberButtons)
        assertEquals(stateColor(hass.states.getValue("climate.heatpump")), model.featureColor)
    }
}
