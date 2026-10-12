package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.tileBadge
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.moreinfo.SliderMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The card features ported after the first ones, for the test instance's demo entities; expectations from upstream. */
class MoreTileFeaturesTest {
    private val hass = GoldenFixture("test-instance").hass

    private fun featureOf(entity: String, config: String): TileFeature? = hass.tileFeatures(
        CardConfig(json("""{"type": "tile", "entity": "$entity", "features": [$config]}""")),
    ).singleOrNull()

    private fun feature(entity: String, type: String, options: String = ""): TileFeature? = featureOf(entity, """{"type": "$type"$options}""")

    @Test
    fun `Given a thermostat when deriving HVAC modes then they are icons in reverse of upstream's order`() {
        val select = feature("climate.hvac", "climate-hvac-modes") as TileFeature.Select
        assertEquals(listOf("off", "fan_only", "dry", "cool", "heat", "auto"), select.options.map { it.value })
        assertEquals("cool", select.selected)
        assertEquals("mdi:snowflake", select.options.first { it.value == "cool" }.icon)
        assertEquals(stateColor(hass.states.getValue("climate.hvac")), select.color)
        val heat = select.options.first { it.value == "heat" }.action
        assertEquals("set_hvac_mode", heat.service)
        assertEquals(json("""{"entity_id": "climate.hvac", "hvac_mode": "heat"}"""), heat.data)
    }

    @Test
    fun `Given configured HVAC modes when deriving then they keep the configured order`() {
        val select = feature("climate.hvac", "climate-hvac-modes", """, "hvac_modes": ["off", "heat", "cool", "auto", "heat_cool"]""")
            as TileFeature.Select
        assertEquals(listOf("off", "heat", "cool", "auto"), select.options.map { it.value })
    }

    @Test
    fun `Given the dropdown style when deriving HVAC modes then it is a menu with the thermostat icon`() {
        val menu = feature("climate.hvac", "climate-hvac-modes", """, "style": "dropdown"""") as TileFeature.Menu
        assertEquals("mdi:thermostat", menu.icon)
        assertFalse(menu.optionIcons)
        assertEquals("cool", menu.value)
    }

    @Test
    fun `Given climate attribute modes when deriving then they are menus only where supported`() {
        val fan = feature("climate.hvac", "climate-fan-modes") as TileFeature.Menu
        assertEquals(listOf("on_low", "on_high", "auto_low", "auto_high", "off"), fan.options.map { it.value })
        assertEquals("on_high", fan.value)
        assertEquals("mdi:fan", fan.icon)
        assertTrue(fan.hideLabel)
        assertEquals("set_fan_mode", fan.options.first().action.service)
        assertNull(feature("climate.hvac", "climate-preset-modes"))
        val presets = feature("climate.ecobee", "climate-preset-modes") as TileFeature.Menu
        assertEquals(listOf("home", "eco", "away"), presets.options.map { it.value })
        val swing = feature("climate.hvac", "climate-swing-horizontal-modes", """, "style": "icons"""")
        assertTrue(swing is TileFeature.Select)
    }

    @Test
    fun `Given a water heater when deriving operation modes then they follow upstream's order reversed`() {
        val select = feature("water_heater.demo_water_heater", "water-heater-operation-modes") as TileFeature.Select
        assertEquals(
            listOf("off", "high_demand", "performance", "eco", "heat_pump", "gas", "electric"),
            select.options.map { it.value },
        )
        assertEquals("eco", select.selected)
    }

    @Test
    fun `Given selects when deriving options then the menu calls their own domain without icons`() {
        val menu = feature("input_select.house_mode", "select-options") as TileFeature.Menu
        assertEquals(listOf("Home", "Away", "Night", "Vacation"), menu.options.map { it.value })
        assertEquals("Away", menu.value)
        assertNull(menu.icon)
        val action = menu.options.first().action
        assertEquals("input_select" to "select_option", action.domain to action.service)
        assertEquals(JsonPrimitive("Home"), action.data?.get("option"))
        // The icons style isn't allowed
        assertTrue(feature("select.speed", "select-options", """, "style": "icons"""") is TileFeature.Menu)
    }

    @Test
    fun `Given a media player when deriving its source then the menu shows its label`() {
        val menu = feature("media_player.lounge_room", "media-player-source") as TileFeature.Menu
        assertFalse(menu.hideLabel)
        assertEquals(listOf("dvd", "youtube"), menu.options.map { it.value })
        assertNull(feature("media_player.living_room_tv", "media-player-source"))
    }

    @Test
    fun `Given a counter when deriving actions then decrement, reset and increment call the counter`() {
        val buttons = (feature("counter.coffee_cups", "counter-actions") as TileFeature.Buttons).buttons
        assertEquals(listOf("decrement", "reset", "increment"), buttons.map { it.action.service })
        assertEquals(listOf("mdi:minus", "mdi:restore", "mdi:plus"), buttons.map { it.icon })
    }

    @Test
    fun `Given a docked vacuum when deriving commands then start, stop and return home follow its state`() {
        val buttons = (feature("vacuum.demo_vacuum_0_ground_floor", "vacuum-commands") as TileFeature.Buttons).buttons
        assertEquals(listOf("start", "stop", "return_to_base"), buttons.map { it.action.service })
        assertEquals(listOf(true, false, true), buttons.map { it.enabled })
    }

    @Test
    fun `Given a mower that can only start when deriving commands then it shows start even while mowing`() {
        val buttons = (feature("lawn_mower.simple_mower", "lawn-mower-commands") as TileFeature.Buttons).buttons
        assertEquals(listOf("start_mowing"), buttons.map { it.action.service })
    }

    @Test
    fun `Given an update asking about backups when deriving actions then install asks first`() {
        val (skip, install) = (feature("update.demo_add_on", "update-actions", """, "backup": "ask"""") as TileFeature.Buttons)
            .buttons
        assertTrue(skip.enabled && install.enabled)
        val question = checkNotNull(install.question)
        assertEquals(JsonPrimitive(true), question.yes.data?.get("backup"))
        assertEquals(JsonPrimitive(false), question.no.data?.get("backup"))
        val noUpdate = (feature("update.demo_no_update", "update-actions") as TileFeature.Buttons).buttons
        assertEquals(listOf(false, false), noUpdate.map { it.enabled })
    }

    @Test
    fun `Given valves when deriving open and close then a known state is a switch`() {
        val switch = feature("valve.front_garden", "valve-open-close") as TileFeature.Switch
        assertTrue(switch.checked)
        assertEquals("close_valve", switch.turnOff.service)
    }

    @Test
    fun `Given covers and valves when deriving positions then sliders are inverted, tilts a cursor`() {
        val position = (feature("cover.living_room_window", "cover-position") as TileFeature.ValueSlider).slider
        assertEquals(70.0, position.value)
        assertTrue(position.inverted && position.showHandle)
        val tilt = (feature("cover.living_room_window", "cover-tilt-position") as TileFeature.ValueSlider).slider
        assertEquals(SliderMode.Cursor, tilt.mode)
        assertEquals(50.0, tilt.value)
        assertNull(feature("valve.back_garden", "cover-position"))
        val valve = (feature("valve.back_garden", "valve-position") as TileFeature.ValueSlider).slider
        assertEquals("set_valve_position", valve.service.service)
        val tiltButtons = (feature("cover.pergola_roof", "cover-tilt") as TileFeature.Buttons).buttons
        assertEquals(listOf("open_cover_tilt", "stop_cover_tilt", "close_cover_tilt"), tiltButtons.map { it.action.service })
    }

    @Test
    fun `Given numbers when deriving numeric input then a slider, or buttons with the buttons style`() {
        val slider = (feature("number.pwm_1", "numeric-input") as TileFeature.ValueSlider).slider
        assertEquals(listOf(0.42, 0.0, 1.0, 0.01), listOf(slider.value, slider.min, slider.max, slider.step))
        val buttons = (feature("input_number.bedroom_brightness", "numeric-input", """, "style": "buttons"""") as TileFeature.NumberButtons)
            .items.single()
        assertEquals(5.0, buttons.step)
        assertEquals(json("""{"entity_id": "input_number.bedroom_brightness", "value": 10}"""), buttons.service?.withValue(10.0)?.data)
    }

    @Test
    fun `Given a media player when deriving volume then it is sent as a fraction, with a mute button`() {
        val slider = feature("media_player.living_room_tv", "media-player-volume-slider") as TileFeature.ValueSlider
        assertEquals(100.0, slider.slider.value)
        assertEquals(JsonPrimitive(0.35), slider.slider.service.withValue(35.0).data?.get("volume_level"))
        assertEquals("volume_mute", slider.trailing?.action?.service)
        val buttons = feature("media_player.living_room_tv", "media-player-volume-buttons", """, "show_mute_button": false""")
            as TileFeature.NumberButtons
        assertNull(buttons.trailing)
        assertEquals(5.0, buttons.items.single().step)
    }

    @Test
    fun `Given a playing media player when deriving playback then power and pause show`() {
        val buttons = (feature("media_player.living_room_tv", "media-player-playback") as TileFeature.Buttons).buttons
        assertEquals(listOf("turn_off", "media_pause"), buttons.map { it.action.service })
    }

    @Test
    fun `Given toggles when deriving then a known state is a switch and an assumed one two buttons`() {
        val switch = feature("switch.ac", "toggle") as TileFeature.Switch
        assertFalse(switch.checked)
        val buttons = (feature("switch.decorative_lights", "toggle") as TileFeature.Buttons).buttons
        assertEquals(listOf("turn_off" to false, "turn_on" to true), buttons.map { it.action.service to it.active })
    }

    @Test
    fun `Given a fan when deriving oscillation and direction then they are icons`() {
        val oscillate = feature("fan.living_room_fan", "fan-oscillate") as TileFeature.Select
        assertEquals(listOf("no", "yes"), oscillate.options.map { it.value })
        val direction = feature("fan.living_room_fan", "fan-direction") as TileFeature.Select
        assertEquals(listOf("forward", "reverse"), direction.options.map { it.value })
    }

    @Test
    fun `Given date entities when setting a date then each domain gets its service`() {
        val date = feature("date.date", "date-set") as TileFeature.DateSet
        assertEquals(LocalDate.of(2020, 1, 1), date.date)
        val day = LocalDate.of(2026, 10, 12)
        assertEquals(json("""{"entity_id": "date.date", "date": "2026-10-12"}"""), date.request.call(day, ZoneOffset.UTC).data)
        val dateTime = feature("datetime.date_and_time", "date-set") as TileFeature.DateSet
        assertEquals(
            json("""{"entity_id": "datetime.date_and_time", "datetime": "2026-10-12T12:00:00.000Z"}"""),
            dateTime.request.call(day, ZoneOffset.UTC).data,
        )
        assertNull(feature("input_datetime.wake_up", "date-set"))
    }

    @Test
    fun `Given a cooling thermostat when deriving its badge then it shows the action in the cool colour`() {
        val state = hass.states.getValue("climate.hvac")
        val badge = checkNotNull(hass.tileBadge(state))
        assertEquals("mdi:snowflake", badge.icon)
        assertEquals(stateColor(state, "cool"), badge.color)
        val heatPump = hass.tileBadge(hass.states.getValue("climate.heatpump"))
        assertEquals(stateColor(hass.states.getValue("climate.heatpump"), "heat"), heatPump?.color)
        assertNull(hass.tileBadge(hass.states.getValue("person.dev")))
        assertNull(hass.tileBadge(hass.states.getValue("light.kitchen_lights")))
        val unavailable = state.copy(state = "unavailable")
        assertEquals(DisplayColor.Theme("orange"), hass.tileBadge(unavailable)?.color)
    }
}
