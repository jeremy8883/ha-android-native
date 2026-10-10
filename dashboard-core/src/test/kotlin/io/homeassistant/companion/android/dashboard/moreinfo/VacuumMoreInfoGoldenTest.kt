package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of vacuums' details against the real frontend's `more-info-vacuum` (20260624.6): vacuums with
 * every command, some and none, each as it is, off, unavailable, cleaning, returning, paused, in error and idle,
 * with a battery level and with a status, and each command's and the fan speed's call.
 */
class VacuumMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured vacuums when deriving the details then they match the header, robot, buttons and menu`() = fixture.controlVariants(VACUUM) { hass, state, captured ->
        val main = captured.obj("main")!!
        val vacuum = main.obj("vacuum")!!
        val info = hass.vacuumMoreInfo(state)!!
        val expected = listOf(
            main.string("state"),
            vacuum.string("battery"),
            vacuum.string("batteryIcon"),
            vacuum.string("visual"),
            vacuum.string("statusColor"),
            vacuum.objects("buttons").map { it.string("label") to (it.boolean("disabled") != true) },
            main.capturedMenus(),
        )
        val actual = listOf(
            info.state,
            info.battery?.text,
            info.battery?.icon,
            info.visual.toString().lowercase(),
            info.color?.css(),
            info.buttons.map { it.label to it.enabled },
            menusText(listOfNotNull(info.fanSpeed)),
        )
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured vacuums when using each control then the calls match the frontend's`() = fixture.controlVariants(VACUUM) { hass, state, captured ->
        val info = hass.vacuumMoreInfo(state)!!
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val actual = when (call.string("control")) {
                "button" -> info.buttons[label.substringAfterLast(' ').toInt()].action
                else -> info.fanSpeed!!.options.single { it.value == label.substringAfterLast(' ') }.action
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private companion object {
        const val VACUUM = "vacuum"
    }
}
