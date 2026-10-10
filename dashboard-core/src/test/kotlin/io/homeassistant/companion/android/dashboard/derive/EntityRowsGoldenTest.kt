package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.recordedCalls
import io.homeassistant.companion.android.dashboard.moreinfo.withState
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the entities card's input rows against the real frontend's (20260624.6, rendered in a
 * `hui-entities-card` by tools/golden/capture.mjs, more-info/controls.json `rows`): numbers as sliders and boxes,
 * selects, texts (with limits, a pattern and a password), a timer counting down and paused, and dates and times,
 * each as it is and unavailable, with each control's call.
 */
class EntityRowsGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured rows when deriving their controls then they match the frontend's rows`(): List<DynamicTest> = rows { control, captured ->
        val row = captured.obj("row")!!
        when (control) {
            is RowSlider -> {
                val slider = row.obj("slider")!!
                assertEquals(
                    listOf(slider.number("value"), slider.number("min"), slider.number("max"), slider.number("step"), slider.boolean("disabled") != true, row.string("state")),
                    listOf(control.value, control.min, control.max, control.step, control.enabled, control.state),
                )
            }
            is RowNumberBox -> {
                val input = row.obj("input")!!
                // Upstream shows "NaN" or the raw state when it isn't a number; the box shows the state instead
                val value = input.string("value")!!.takeIf { it.toDoubleOrNull()?.isNaN() == false }
                assertEquals(
                    listOf(value, input.number("min"), input.number("max"), input.number("step"), input.string("unit"), input.boolean("disabled") != true),
                    listOf(control.value.takeIf { value != null }, control.min, control.max, control.step, control.unit, control.enabled),
                )
            }
            is RowSelect -> {
                val select = row.obj("select")!!
                assertEquals(
                    listOf(select.string("label"), select.string("value"), select.boolean("disabled") != true, select.objects("options").map { it.string("value") to it.string("label") }),
                    listOf(control.label, control.value, control.enabled, control.options),
                )
            }
            is RowTextInput -> {
                val input = row.obj("input")!!
                assertEquals(
                    listOf(input.string("label"), input.string("value"), input.number("minlength")?.toInt(), input.number("maxlength")?.toInt(), input.string("pattern"), input.string("type") == "password", input.string("placeholder"), input.boolean("disabled") != true),
                    listOf(control.label, control.value, control.minLength, control.maxLength, control.pattern, control.password, control.placeholder, control.enabled),
                )
            }
            is RowDateTime -> {
                val date = row.obj("date")
                val time = row.obj("time")
                assertEquals(
                    listOf(date != null, date?.string("value"), time != null, time?.string("value"), (date ?: time)!!.boolean("disabled") != true, date?.string("label")),
                    listOf(control.hasDate, control.date, control.hasTime, control.time, control.enabled, control.label),
                )
            }
            is RowTimer -> {
                val shown = row.string("state")!!
                val at = Instant.ofEpochMilli(captured.number("capturedAt")!!.toLong())
                // Unavailable, upstream shows "0"; the row shows the state instead
                if (captured.obj("stateObj")!!.string("state") == "unavailable") {
                    assertEquals("Unavailable", control.display(at))
                } else {
                    // The capture reads its clock a moment after drawing the row
                    val candidates = (0..WINDOW_SECONDS).map { control.display(at.minusSeconds(it.toLong())) }
                    assertTrue(shown in candidates, "$shown not in $candidates")
                }
            }
            else -> {
                val buttons = row.objects("buttons").map { it.string("text") to (it.boolean("disabled") != true) }
                assertEquals(buttons, (control as? RowControl.Buttons)?.buttons?.map { it.label to it.enabled }.orEmpty())
            }
        }
    }

    @TestFactory
    fun `Given captured rows when using each control then the calls match the frontend's`(): List<DynamicTest> = rows { control, captured ->
        captured.objects("calls").forEach { call ->
            val stateObj = captured.obj("stateObj")!!
            val actual = when (call.string("control")) {
                "slider" -> (control as RowSlider).service.withValue(
                    (stateObj.obj("attributes")!!.number("min") ?: 0.0) + (stateObj.obj("attributes")!!.number("step") ?: 1.0),
                )
                "input" -> when (control) {
                    is RowNumberBox -> control.set("7")
                    else -> (control as RowTextInput).set("abc")
                }
                "select" -> (control as RowSelect).let { select -> select.choose(select.options.first { it.first != select.value }.first) }
                "date" -> (control as RowDateTime).setDate("2024-02-03")
                "time" -> (control as RowDateTime).setTime("08:15:00")
                else -> (control as RowControl.Buttons).buttons.single().action
            }
            assertEquals(call.recordedCalls(), listOf(actual), call.string("control"))
        }
    }

    private fun rows(check: (RowControl?, JsonObject) -> Unit): List<DynamicTest> = fixture.json("more-info/controls.json").obj("rows")!!.flatMap { (entityId, variants) ->
        (variants as JsonObject).map { (name, captured) ->
            DynamicTest.dynamicTest("$entityId $name") {
                val (hass, _) = fixture.withState((captured as JsonObject).obj("stateObj")!!)
                val card = CardConfig(JsonObject(mapOf("type" to JsonPrimitive("entities"), "entities" to JsonArray(listOf(JsonPrimitive(entityId))))))
                check(hass.entitiesModel(card, Instant.EPOCH).rows.single().control, captured)
            }
        }
    }

    private companion object {
        const val WINDOW_SECONDS = 15
    }
}
