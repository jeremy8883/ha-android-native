package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Every entity of the test instance's state card (`state-card-content`, as the details lead with it and a group
 * lists its members), as is and unavailable, against what the frontend (20260624.6) drew (tools/golden/capture.mjs,
 * more-info/controls.json `stateCards`): its name, the text on its right, and its buttons, switch or input.
 */
class StateCardsGoldenTest {
    private val fixture = GoldenFixture("test-instance")
    private val captured = fixture.json("more-info/controls.json").obj("stateCards")!!
    private val states = captured.obj("states")!!
    private val hass: HassSnapshot = fixture.hass.copy(
        states = parseStates(JsonArray(states.values.toList())),
        services = captured.obj("services")!!.mapValues { (_, actions) -> actions.jsonObject.mapValues { it.value.jsonObject } },
    )

    @TestFactory
    fun `Given each entity when drawing its state card then it shows what the frontend's does`(): List<DynamicTest> = captured.obj("cards")!!.flatMap { (id, variants) ->
        (variants as JsonObject).map { (variant, drawn) ->
            DynamicTest.dynamicTest("$id $variant") { assertCard(id, variant, drawn.jsonObject) }
        }
    }

    private fun assertCard(id: String, variant: String, drawn: JsonObject) {
        val original = states.obj(id)!!
        val shown = if (variant == "unavailable") JsonObject(original + ("state" to JsonPrimitive("unavailable"))) else original
        val snapshot = hass.copy(states = parseStates(JsonArray(states.values.map { if (it.jsonObject.string("entity_id") == id) shown else it })))
        val card = snapshot.stateCard(snapshot.states[id]!!, Instant.now())
        val right = card.right
        // Dropdowns and text fields carry the name as their label, in its place
        val labelled = right.control is RowSelect || (right.control as? RowTextInput)?.label?.isNotEmpty() == true
        assertEquals(drawn.string("name"), card.name.takeUnless { labelled }, "name")
        when (val control = right.control) {
            null, is RowTimer -> assertEquals(squash(drawn.string("right")), squash(listOfNotNull(right.state, right.secondary).joinToString(" ")), "state")
            is RowControl.Buttons -> assertEquals(
                drawn.list("buttons").filter { it.string("text") != null }.map { it.string("text") to !it.boolean("disabled")!! },
                control.buttons.map { it.label to it.enabled },
                "buttons",
            )
            is RowControl.IconButtons -> {
                val buttons = drawn.obj("toggle")?.let { toggle ->
                    (toggle["buttons"] as JsonArray).map { (it as JsonPrimitive).content to null }
                } ?: drawn.list("iconButtons").map { it.string("label") to !it.boolean("disabled")!! }
                assertEquals(buttons, control.buttons.map { it.label to it.enabled.takeIf { _ -> drawn.obj("toggle") == null } }, "icon buttons")
            }
            is RowControl.Toggle -> {
                val toggle = drawn.obj("toggle")!!
                assertEquals(toggle.boolean("checked"), control.checked, "checked")
                assertEquals(toggle.boolean("disabled"), !control.enabled, "disabled")
            }
            is RowSlider -> assertEquals((drawn.obj("slider")!!["value"] as JsonPrimitive).content.toDouble(), control.value, "slider")
            // Its menu (`ha-control-select-menu`) isn't read by the capture; only that it shows one
            is RowSelect -> assertEquals(true, drawn.string("tag")?.endsWith("select"), "select")
            is RowTextInput, is RowNumberBox -> assertEquals(true, drawn["input"] !is JsonNull && drawn["input"] != null, "input")
            else -> error("unexpected control $control")
        }
    }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().map { it.jsonObject }

    /** Text without its spacing, which the browser's layout decides. */
    private fun squash(text: String?): String = text.orEmpty().filterNot { it.isWhitespace() }
}
