package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of fans' details against the real frontend's `more-info-fan` (20260624.6): fans with three
 * speeds (the buttons), with presets, direction and oscillation, and with presets only (the switch), each as it is,
 * off, unavailable, on at a speed and on with a fine step (the slider), with each control's call.
 */
class FanMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured fans when deriving the speed buttons then they match ha-control-select`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("speedSelect")?.let { select ->
            listOf(
                select.string("value"),
                select.string("label"),
                select.boolean("disabled") != true,
                select.string("color"),
                select.objects("options").map { it.string("value") to it.string("label") },
            )
        }
        val actual = (hass.fanMoreInfo(state)!!.speed as? FanSpeedControl.Buttons)?.select?.let { buttons ->
            listOf(
                buttons.value,
                buttons.label,
                buttons.enabled,
                buttons.color?.css(),
                buttons.options.map { it.value to it.label },
            )
        }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured fans when deriving the speed slider then it matches ha-control-slider`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("speedSlider")?.let {
            listOf(
                it.number("value"),
                it.number("step"),
                it.boolean("showHandle"),
                it.boolean("disabled") != true,
                it.string("label"),
                it.string("unit"),
                it.string("color"),
            )
        }
        val actual = ((hass.fanMoreInfo(state)!!.speed as? FanSpeedControl.Slider)?.slider)?.let {
            listOf(it.value, it.step, it.showHandle, it.enabled, it.label, it.unit, it.color?.css())
        }
        assertEquals(expected, actual)
        val power = captured.obj("main")!!.obj("power")?.let { it.boolean("disabled") != true }
        val info = hass.fanMoreInfo(state)!!
        assertEquals(power, info.power?.let { info.powerEnabled })
    }

    @TestFactory
    fun `Given captured fans when deriving the menus then they match the ha-control-select-menus`() = each { hass, state, captured ->
        val menus = hass.fanMoreInfo(state)!!.menus
        assertEquals(captured.obj("main")!!.capturedMenus(), menusText(menus))
        captured.obj("menuIcons")?.values?.map { it as JsonObject }?.filter { it.isNotEmpty() }?.forEach { icons ->
            // Recorded for every fan with presets, shown only for those that support setting them
            val menu = menus.singleOrNull { menu -> menu.options.map { it.value }.toSet() == icons.keys } ?: return@forEach
            assertEquals(icons.mapValues { it.value.stringOrNull }, menu.options.associate { it.value to it.icon })
        }
    }

    @TestFactory
    fun `Given captured fans when using each control then the calls match the frontend's`() = each { hass, state, captured ->
        val info = hass.fanMoreInfo(state)!!
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val value = label.substringAfterLast(' ')
            val actual = when (call.string("control")) {
                // Upstream sets 0% for a speed it doesn't name
                "speed" -> (info.speed as FanSpeedControl.Buttons).select.options.singleOrNull { it.value == value }?.action
                    ?: return@forEach
                "slider" -> (info.speed as FanSpeedControl.Slider).slider.service.withValue(value.toDouble())
                "power" -> info.power!!
                "toggle" -> info.toggle!!.let { if (it.checked) it.turnOff else it.turnOn }
                else -> info.menus.single { it.label == label.substringBeforeLast(' ') }.options.single { it.value == value }.action
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private fun each(check: (HassSnapshot, EntityState, JsonObject) -> Unit): List<DynamicTest> = fixture.controlVariants("fan", check = check)
}
