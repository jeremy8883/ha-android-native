package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of a thermostat's details against the real frontend's `more-info-climate` (20260624.6): a
 * single target, a target with humidity and every menu, and a range, each as it is, off and unavailable, with the
 * call of each control recorded by the capture.
 */
class ClimateMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured thermostats when deriving the current readings then they match the current row`() = fixture.controlVariants(CLIMATE) { hass, state, captured ->
        val expected = captured.obj("main")!!.objects("current").map { it.string("label") to it.string("value") }
        assertEquals(expected, hass.climateMoreInfo(state)!!.current)
    }

    @TestFactory
    fun `Given captured thermostats when deriving the temperature dial then it matches ha-state-control-climate-temperature`() = fixture.controlVariants(CLIMATE) { hass, state, captured ->
        assertCircular(captured.obj("main")!!.obj("circular")!!, hass.climateMoreInfo(state)!!.temperature)
    }

    @TestFactory
    fun `Given captured thermostats with a target humidity when deriving the humidity dial then it matches`() = fixture.controlVariants(CLIMATE) { hass, state, captured ->
        val humidity = captured.obj("pickers")!!.obj("humidity")?.obj("circular")
        val control = hass.climateMoreInfo(state)!!.humidity
        assertEquals(humidity != null, control != null)
        if (humidity != null && control != null) assertCircular(humidity, control)
    }

    @TestFactory
    fun `Given captured thermostats when deriving the menus then they match the ha-control-select-menus`() = fixture.controlVariants(CLIMATE) { hass, state, captured ->
        val menus = hass.climateMoreInfo(state)!!.menus
        assertEquals(captured.obj("main")!!.capturedMenus(), menusText(menus))
        captured.obj("menuIcons")?.values?.map { it as JsonObject }?.filter { it.isNotEmpty() }?.forEach { icons ->
            // Each attribute menu's option icons (the mode menu's are built in), found by its options
            val menu = menus.single { menu -> menu.options.map { it.value }.toSet() == icons.keys }
            assertEquals(icons.mapValues { it.value.stringOrNull }, menu.options.associate { it.value to it.icon })
        }
    }

    @TestFactory
    fun `Given captured thermostats when setting each control then the calls match the frontend's`() = fixture.controlVariants(CLIMATE, onlyAsIs = true) { hass, state, captured ->
        captured.objects("calls").forEach { call ->
            assertEquals(call.recordedCalls(), listOfNotNull(hass.climateCall(state, call)), call.string("label"))
        }
    }

    /** What the native controls call for one of the frontend's recorded interactions. */
    private fun HassSnapshot.climateCall(state: EntityState, call: JsonObject): CardAction.CallService? {
        val label = call.string("label")!!
        val targets = climateTargets(state)
        val step = climateTemperatureStep(state)
        val min = state.attributes.number("min_temp") ?: 0.0
        val max = state.attributes.number("max_temp") ?: 0.0
        val range = targets.value == null
        val first = if (range) CircularTarget.Low else CircularTarget.Value
        return when (call.string("control")) {
            "temperature" -> when {
                label == "value 21" -> climateTemperatureCall(state, targets.with(CircularTarget.Value, 21.0), false)
                label == "low 19" -> climateTemperatureCall(state, targets.with(CircularTarget.Low, 19.0), true)
                label == "high 25" -> climateTemperatureCall(state, targets.with(CircularTarget.High, 25.0), true)
                label == "high button plus" ->
                    climateTemperatureCall(state, targets.stepped(CircularTarget.High, step, min, max), true)
                else -> {
                    // Pressed twice before the debounced call
                    val sign = if (label.endsWith("minus")) -1 else 1
                    val pressed = targets.stepped(first, sign * step, min, max).stepped(first, sign * step, min, max)
                    climateTemperatureCall(state, pressed, range)
                }
            }
            "humidity" -> {
                val humidity = state.attributes.number("humidity")!!
                val value = if (label == "value 55") {
                    55.0
                } else {
                    val step = state.attributes.number("target_humidity_step") ?: 1.0
                    clamp(humidity + step, state.attributes.number("min_humidity") ?: 0.0, state.attributes.number("max_humidity") ?: 100.0)
                }
                climateHumidityCall(state, value)
            }
            else -> {
                val menus = climateMoreInfo(state)!!.menus
                val (menuLabel, option) = label.substringBeforeLast(' ') to label.substringAfterLast(' ')
                menus.single { it.label == menuLabel }.options.single { it.value == option }.action
            }
        }
    }

    private companion object {
        const val CLIMATE = "climate"
    }
}
