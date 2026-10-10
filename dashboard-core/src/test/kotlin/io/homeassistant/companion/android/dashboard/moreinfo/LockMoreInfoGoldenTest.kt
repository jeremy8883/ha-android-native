package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of locks' details against the real frontend's `more-info-lock` (20260624.6): a lock, an
 * unlocked one and one that opens, each as it is, off, unavailable, jammed, unknown, locking, unlocking and open,
 * with each control's call (the open button confirmed with a second tap).
 */
class LockMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured locks when deriving the switch icons then they match ha-state-control-lock-toggle`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("toggle")?.array("icons")?.map { it.stringOrNull }
            ?.map { hass.entityIcon(state.entityId, stateValue = it) }
        val toggle = hass.lockMoreInfo(state)!!.toggle
        assertEquals(expected, toggle?.let { listOf(it.onIcon, it.offIcon) })
    }

    @TestFactory
    fun `Given captured locks when deriving the jammed state and the open button then they match`() = each { hass, state, captured ->
        val lock = captured.obj("main")!!.obj("lock")!!
        val info = hass.lockMoreInfo(state)!!
        assertEquals(lock.boolean("status"), info.jammed != null)
        assertEquals(lock.array("jammed")!!.map { it.stringOrNull }, info.jammed?.buttons?.map { it.first }.orEmpty())
        val open = lock.obj("open")?.let { it.string("text") to (it.boolean("disabled") != true) }
        assertEquals(open, info.open?.let { it.label to it.enabled })
        captured.string("openConfirm")?.let { assertEquals(it, info.open?.confirmLabel) }
    }

    @TestFactory
    fun `Given captured locks when using each control then the calls match the frontend's`() = each { hass, state, captured ->
        val info = hass.lockMoreInfo(state)!!
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val actual = when (call.string("control")) {
                "toggle" -> info.toggle!!.let { if (it.checked) it.turnOff else it.turnOn }
                "toggle-button" -> info.toggle!!.let { if (label == "lock") it.turnOn else it.turnOff }
                "jammed" -> info.jammed!!.buttons[if (label == "unlock") 0 else 1].second
                else -> info.open!!.action
            }
            assertEquals(call.recordedCalls(), listOf(actual), label)
        }
    }

    private fun each(check: (HassSnapshot, EntityState, JsonObject) -> Unit): List<DynamicTest> = fixture.controlVariants("lock", check = check)
}
