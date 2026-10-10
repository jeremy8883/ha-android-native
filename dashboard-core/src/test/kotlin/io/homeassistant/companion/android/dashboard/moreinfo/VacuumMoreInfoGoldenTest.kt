package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
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

    @TestFactory
    fun `Given captured vacuums when deriving the clean-by-area view then it matches the areas, empty state and call`() = fixture.controlVariants(VACUUM, onlyAsIs = true) { hass, state, captured ->
        val shown = captured.obj("cleanAreas") ?: return@controlVariants
        val view = hass.vacuumCleanAreas(state, captured.obj("entry"))!!
        assertEquals(
            shown.objects("sections").map { section ->
                section.string("label") to section.objects("areas").map { it.string("areaId") to it.string("name") }
            },
            view.sections.map { section -> section.label to section.areas.map { it.areaId to it.name } },
        )
        assertEquals(shown.obj("empty")?.string("title"), view.empty?.title)
        assertEquals(shown.obj("empty")?.string("configure"), view.empty?.configureLabel)
        if (view.empty == null) {
            assertEquals(shown.string("hint"), view.hint)
            assertEquals(shown.string("start"), view.startLabel)
            val order = shown.array("order")!!.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            assertEquals(shown.recordedCalls(), listOf(view.clean(order)))
        }
    }

    private companion object {
        const val VACUUM = "vacuum"
    }
}
