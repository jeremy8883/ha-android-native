package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of an alarm panel's details against the real frontend's `more-info-alarm_control_panel`
 * (20260624.6): a panel with every mode and a code, as it is, off, unavailable, armed, triggered, arming and
 * pending, and without a code (so its calls go straight out) with each mode's call and the disarm button's.
 */
class AlarmMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured alarm panels when deriving the details then they match the modes and status`() = fixture.controlVariants(ALARM) { hass, state, captured ->
        val alarm = captured.obj("main")!!.obj("alarm")!!
        val expected = listOf(
            alarm.obj("modes")?.let { modes ->
                listOf(
                    modes.string("value"),
                    modes.string("label"),
                    modes.boolean("disabled") != true,
                    modes.string("color"),
                    modes.objects("options").map { it.string("value") to it.string("label") },
                )
            },
            alarm.boolean("status"),
            alarm.string("disarm"),
        )
        val info = hass.alarmMoreInfo(state)!!
        val actual = listOf(
            info.modes?.let { modes ->
                listOf(modes.value, modes.label, modes.enabled, modes.color?.css(), modes.options.map { it.value to it.label })
            },
            info.status != null,
            info.disarm?.first,
        )
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured alarm panels when using each control then the calls match the frontend's`() = fixture.controlVariants(ALARM) { hass, state, captured ->
        val info = hass.alarmMoreInfo(state)!!
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val actual = when (call.string("control")) {
                "mode" -> info.modes!!.options.single { it.value == label }.action
                else -> info.disarm!!.second
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private companion object {
        const val ALARM = "alarm_control_panel"
    }
}
