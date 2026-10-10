package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of lawn mowers' and a remote's details against the real frontend's `more-info-lawn_mower`
 * and `more-info-remote` (20260624.6), with the test instance's own devices (tools/test-ha's `test_devices`): a
 * mower that does everything with a battery on its device, one that only starts, and one heading home, each in
 * every activity, off and unavailable, and a remote with activities, with each control's call.
 */
class LawnMowerRemoteGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured lawn mowers when deriving the details then they match the battery, drawing and buttons`() = fixture.controlVariants(MOWER) { hass, state, captured ->
        val mower = captured.obj("main")!!.obj("mower")!!
        val info = hass.lawnMowerMoreInfo(state)!!
        assertEquals(
            listOf(
                mower.string("battery"),
                mower.string("visual"),
                mower.string("color"),
                mower.objects("buttons").map { it.string("label") to (it.boolean("disabled") != true) },
            ),
            listOf(
                info.battery?.text,
                info.visual.toString().lowercase(),
                info.color?.css(),
                info.buttons.map { it.label to it.enabled },
            ),
        )
    }

    @TestFactory
    fun `Given captured lawn mowers and remotes when using each control then the calls match the frontend's`() = listOf(MOWER, REMOTE).flatMap { domain ->
        fixture.controlVariants(domain) { hass, state, captured ->
            captured.objects("calls").forEach { call ->
                val label = call.string("label")!!
                val actual = when (call.string("control")) {
                    "button" -> hass.lawnMowerMoreInfo(state)!!.buttons[label.substringAfterLast(' ').toInt()].action
                    else -> hass.remoteActivity(state)!!.options.single { it.value == label.removePrefix("activity ") }.action
                }
                assertEquals(call.recordedCalls(), listOf(actual), label)
            }
        }
    }

    @TestFactory
    fun `Given captured remotes when deriving the activity menu then it matches ha-select`() = fixture.controlVariants(REMOTE) { hass, state, captured ->
        val select = captured.obj("main")!!.obj("remote")
        val menu = hass.remoteActivity(state)
        assertEquals(
            select?.let { listOf(it.string("label"), it.string("value"), it.objects("options").map { o -> o.string("value") to o.string("label") }) },
            menu?.let { listOf(it.label, it.value, it.options.map { o -> o.value to o.label }) },
        )
    }

    private companion object {
        const val MOWER = "lawn_mower"
        const val REMOTE = "remote"
    }
}
