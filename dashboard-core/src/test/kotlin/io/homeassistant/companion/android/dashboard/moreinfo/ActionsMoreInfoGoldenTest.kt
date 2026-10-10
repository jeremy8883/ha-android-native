package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the details made of plain buttons against the real frontend (20260624.6):
 * `more-info-counter` (also at its maximum and minimum), `more-info-automation`, `more-info-timer` (idle, active
 * and paused) and `more-info-siren` (with and without its advanced controls), with each button's call.
 */
class ActionsMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    /** The buttons the native details show, with what tapping each calls. */
    private fun HassSnapshot.buttons(state: EntityState): List<Triple<String, Boolean, Any>> = counterActions(state)?.map { Triple(it.label, it.enabled, it.action) }
        ?: automationMoreInfo(state, Instant.EPOCH)?.run?.let { listOf(Triple(it.label, it.enabled, it.action)) }
        ?: timerMoreInfo(state)?.let { timer ->
            timer.buttons.map { button ->
                val action = when (button) {
                    is TimerButton.Start -> timerStartCall(state, timer.duration)
                    is TimerButton.Call -> button.action
                }
                Triple(button.label, true, action)
            }
        }.orEmpty()

    @TestFactory
    fun `Given captured entities when deriving the action buttons then they match the dialogs' buttons`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.objects("actions").map { it.string("text") to (it.boolean("disabled") != true) }
        assertEquals(expected, hass.buttons(state).map { it.first to it.second })
    }

    @TestFactory
    fun `Given captured entities when tapping each button then the calls match the frontend's`() = each { hass, state, captured ->
        val buttons = hass.buttons(state)
        captured.objects("calls").filter { it.string("control") == "action" }.forEach { call ->
            val index = call.string("label")!!.substringAfterLast(' ').toInt()
            assertEquals(call.recordedCalls(), listOf(buttons[index].third), call.string("label"))
        }
    }

    @TestFactory
    fun `Given captured timers when seeding the duration then it matches ha-duration-input`() = fixture.controlVariants("timer") { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("duration")?.let {
            TimerDuration(
                it.number("hours")!!.toInt(),
                it.number("minutes")!!.toInt(),
                it.number("seconds")!!.toInt(),
                it.number("milliseconds")!!.toInt(),
            )
        }
        assertEquals(expected, hass.timerMoreInfo(state)!!.duration)
    }

    @TestFactory
    fun `Given captured automations when deriving the last run then the label matches`() = fixture.controlVariants("automation") { hass, state, captured ->
        val expected = captured.obj("main")!!.string("lastTriggered")!!
        val actual = hass.automationMoreInfo(state, Instant.EPOCH)!!.lastTriggered
        assertTrue(actual.startsWith(expected.substringBefore(':')), "$expected / $actual")
    }

    @TestFactory
    fun `Given captured sirens when deriving the advanced controls button then it matches`() = fixture.controlVariants("siren") { hass, state, captured ->
        assertEquals(captured.obj("main")!!.string("moreControls"), hass.sirenMoreInfo(state)!!.advanced?.title)
    }

    private fun each(check: (HassSnapshot, EntityState, JsonObject) -> Unit): List<DynamicTest> = listOf("counter", "automation", "timer").flatMap { fixture.controlVariants(it, check = check) }
}
