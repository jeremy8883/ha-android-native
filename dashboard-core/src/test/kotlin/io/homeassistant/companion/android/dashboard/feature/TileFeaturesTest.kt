package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tile features for the demo entities of the test instance, with expectations derived from upstream's code. */
class TileFeaturesTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun feature(entity: String, type: String): TileFeature? = hass.tileFeatures(CardConfig(json("""{"type": "tile", "entity": "$entity", "features": [{"type": "$type"}]}"""))).singleOrNull()

    @Test
    fun `Given a dimmable light when deriving brightness then the slider shows its percentage`() {
        val slider = feature("light.kitchen_lights", "light-brightness") as TileFeature.Slider
        assertEquals(71, slider.value)
        assertTrue(slider.showHandle)
        assertEquals(
            json("""{"entity_id": "light.kitchen_lights", "brightness_pct": 40}"""),
            slider.service.withValue(40.0).data,
        )
    }

    @Test
    fun `Given covers when deriving open and close then buttons follow position and state`() {
        val hall = feature("cover.hall_window", "cover-open-close") as TileFeature.Buttons
        assertEquals(listOf("open_cover" to true, "stop_cover" to true, "close_cover" to true), hall.buttons.map { it.action.service to it.enabled })
        val kitchen = feature("cover.kitchen_window", "cover-open-close") as TileFeature.Buttons
        assertEquals(listOf(true, true, false), kitchen.buttons.map { it.enabled })
    }

    @Test
    fun `Given a locked lock when deriving commands then only unlock is enabled and no code is asked`() {
        val buttons = (feature("lock.front_door_deadbolt", "lock-commands") as TileFeature.Buttons).buttons
        assertEquals(listOf("lock" to false, "unlock" to true), buttons.map { it.action.service to it.enabled })
        assertNull(buttons[1].action.code)
    }

    @Test
    fun `Given climates when deriving target temperature then single and range controls match their features`() {
        val single = (feature("climate.hvac", "target-temperature") as TileFeature.NumberButtons).items.single()
        assertEquals(listOf(21.0, 7.0, 35.0, 0.5), listOf(single.value, single.min, single.max, single.step))
        assertEquals(1, single.fractionDigits)
        assertEquals("°C", single.unit)

        val (low, high) = (feature("climate.ecobee", "target-temperature") as TileFeature.NumberButtons).items
        assertEquals(listOf(21.0, 7.0, 24.0), listOf(low.value, low.min, low.max))
        assertEquals(listOf(24.0, 21.0, 35.0), listOf(high.value, high.min, high.max))
        assertEquals(
            json("""{"entity_id": "climate.ecobee", "target_temp_high": 24.0, "target_temp_low": 20.5}"""),
            low.service?.withValue(20.5)?.data,
        )
    }

    @Test
    fun `Given a three speed fan when deriving fan speed then speeds are buttons with upstream's percentages`() {
        val select = feature("fan.living_room_fan", "fan-speed") as TileFeature.Select
        assertEquals(listOf("off", "low", "medium", "high"), select.options.map { it.value })
        assertEquals("off", select.selected)
        assertEquals(listOf(0, 33, 66, 100), select.options.map { (it.action.data?.get("percentage") as JsonPrimitive).content.toInt() })
        assertNull(feature("fan.preset_only_limited_fan", "fan-speed"))
    }

    @Test
    fun `Given a coded alarm when deriving modes then all modes show reversed and arming asks for a code`() {
        val select = feature("alarm_control_panel.security", "alarm-modes") as TileFeature.Select
        assertEquals(
            listOf("disarmed", "armed_custom_bypass", "armed_vacation", "armed_night", "armed_away", "armed_home"),
            select.options.map { it.value },
        )
        assertEquals("disarmed", select.selected)
        val armAway = select.options.first { it.value == "armed_away" }.action
        assertEquals("alarm_arm_away", armAway.service)
        assertNotNull(armAway.code)
        assertEquals("number", armAway.code?.codeFormat)
    }

    @Test
    fun `Given a feature the entity does not support when deriving then it is skipped`() {
        assertNull(feature("lock.front_door_deadbolt", "light-brightness"))
        assertNull(feature("lock.front_door_deadbolt", "unknown-feature"))
    }
}
