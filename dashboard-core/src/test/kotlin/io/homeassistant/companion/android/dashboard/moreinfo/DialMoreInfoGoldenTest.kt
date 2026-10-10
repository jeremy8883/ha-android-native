package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the single-dial details, `more-info-water_heater` and `more-info-humidifier`, against the
 * real frontend (20260624.6): two water heaters and a humidifier, a dehumidifier and a hygrostat with modes, each as
 * it is, off and unavailable, with the call of each control recorded by the capture.
 */
class DialMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    /** What one entity's details derive, by domain. */
    private data class Dial(
        val current: List<Pair<String, String>>,
        val control: CircularControl,
        val menus: List<SelectMenu>,
        val call: (Double) -> CardAction.CallService,
        val target: Double?,
    )

    private fun HassSnapshot.dial(state: EntityState): Dial = waterHeaterMoreInfo(state)?.let {
        Dial(it.current, it.temperature, it.menus, { value -> waterHeaterTemperatureCall(state, value) }, waterHeaterTarget(state))
    } ?: humidifierMoreInfo(state)!!.let {
        Dial(it.current, it.humidity, it.menus, { value -> humidifierHumidityCall(state, value) }, humidifierTarget(state))
    }

    @TestFactory
    fun `Given captured dials when deriving the current readings then they match the current row`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.objects("current").map { it.string("label") to it.string("value") }
        assertEquals(expected, hass.dial(state).current)
    }

    @TestFactory
    fun `Given captured dials when deriving them then they match the frontend's state controls`() = each { hass, state, captured ->
        assertCircular(captured.obj("main")!!.obj("circular")!!, hass.dial(state).control)
    }

    @TestFactory
    fun `Given captured dials when deriving the menus then they match the ha-control-select-menus`() = each { hass, state, captured ->
        val menus = hass.dial(state).menus
        assertEquals(captured.obj("main")!!.capturedMenus(), menusText(menus))
        captured.obj("menuIcons")?.values?.map { it as JsonObject }?.filter { it.isNotEmpty() }?.forEach { icons ->
            val menu = menus.single { menu -> menu.options.map { it.value }.toSet() == icons.keys }
            assertEquals(icons.mapValues { it.value.stringOrNull }, menu.options.associate { it.value to it.icon })
        }
    }

    @TestFactory
    fun `Given captured dials when setting each control then the calls match the frontend's`() = each(onlyAsIs = true) { hass, state, captured ->
        val dial = hass.dial(state)
        val slider = dial.control.slider
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val actual = when {
                label.startsWith("value ") -> dial.call(label.removePrefix("value ").toDouble())
                label.startsWith("button ") -> {
                    // Pressed twice before the debounced call
                    val step = if (label.endsWith("minus")) -slider.step else slider.step
                    val pressed = CircularTargets(dial.target, null, null)
                        .stepped(CircularTarget.Value, step, slider.min, slider.max)
                        .stepped(CircularTarget.Value, step, slider.min, slider.max)
                    dial.call(pressed.value!!)
                }
                else -> {
                    val (menuLabel, option) = label.substringBeforeLast(' ') to label.substringAfterLast(' ')
                    dial.menus.single { it.label == menuLabel }.options.single { it.value == option }.action
                }
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private fun each(onlyAsIs: Boolean = false, check: (HassSnapshot, EntityState, JsonObject) -> Unit): List<DynamicTest> = listOf("water_heater", "humidifier").flatMap { fixture.controlVariants(it, onlyAsIs, check) }
}
