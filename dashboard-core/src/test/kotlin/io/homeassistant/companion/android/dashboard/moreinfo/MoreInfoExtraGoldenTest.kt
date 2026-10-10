package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The details of the domains with controls of their own (sun, date and time, image, group, person, script, camera,
 * weather) against what the frontend (20260624.6) drew for the test instance's entities, as they were and turned
 * off and unavailable (tools/golden/capture.mjs, more-info/controls.json `details`).
 */
class MoreInfoExtraGoldenTest {
    private val fixture = GoldenFixture("test-instance")
    private val controls = fixture.json("more-info/controls.json")
    private val stateCards = controls.obj("stateCards")!!
    private val base: HassSnapshot = fixture.hass.copy(
        states = parseStates(JsonArray(stateCards.obj("states")!!.values.toList())),
        services = stateCards.obj("services")!!.mapValues { (_, actions) -> actions.jsonObject.mapValues { it.value.jsonObject } },
    )

    @TestFactory
    fun `Given the sun when deriving its details then its rows are the frontend's`() = perVariant("sun") { hass, state, drawn ->
        val sun = hass.extra(state) as MoreInfoExtra.Sun
        assertEquals(drawn.list("rows").map { it.string("key") to it.string("value") }, sun.rows.map { it.name to it.value })
        assertEquals(drawn.list("rows").map { it.string("relative") != null }, sun.rows.map { it.relative != null })
    }

    @TestFactory
    fun `Given a date or time entity when deriving its details then its fields are the frontend's`() = listOf("date", "datetime", "time", "input_datetime").flatMap { domain ->
        perVariant(domain) { hass, state, drawn ->
            // "off" isn't a state these entities have (the capture's turned-off variant)
            if (state.state == "off") return@perVariant
            val control = (hass.extra(state) as MoreInfoExtra.DateTime?)?.control
            val date = drawn.obj("date")
            val time = drawn.obj("time")
            if (date == null && time == null) return@perVariant assertNull(control)
            assertEquals(date?.string("value"), control?.date.takeIf { control?.hasDate == true }, "date")
            assertEquals(time?.string("value")?.ifEmpty { null }, control?.time.takeIf { control?.hasTime == true }, "time")
            assertEquals((date ?: time)!!.boolean("disabled"), control?.enabled == false, "disabled")
        }
    }

    @TestFactory
    fun `Given an image when deriving its details then it shows the frontend's picture`() = perVariant("image") { hass, state, drawn ->
        val image = hass.extra(state) as MoreInfoExtra.Image?
        assertEquals(drawn.string("src")?.substringAfter("8124"), image?.path)
    }

    @TestFactory
    fun `Given a group when deriving its details then it lists its members after their domain's details`() = perVariant("group") { hass, state, drawn ->
        val group = hass.extra(state) as MoreInfoExtra.Group
        assertEquals(drawn.strings("members"), group.members.map { it.entityId })
        assertEquals(drawn.string("inner") != null, group.inner != null, "inner details")
    }

    @TestFactory
    fun `Given a person when deriving their details then the map and zone button are the frontend's`() = perVariant("person") { hass, state, drawn ->
        val person = hass.copy(user = hass.user?.copy(isAdmin = true)).extra(state) as MoreInfoExtra.Person
        assertEquals(drawn.boolean("map"), person.location != null, "map")
        assertEquals(drawn.string("createZone"), person.createZone)
    }

    @TestFactory
    fun `Given a script when deriving its details then its header, form and buttons are the frontend's`() = perVariant("script") { hass, state, drawn ->
        val script = (hass.extra(state) as MoreInfoExtra.Script).script
        val model = hass.moreInfoModel(state.entityId, NOW)!!
        assertEquals(drawn.string("state"), model.state, "state")
        assertEquals(drawn.string("description"), script.description)
        assertEquals(drawn.string("queue"), script.queue)
        val fields = drawn.list("fields")
        assertEquals(fields.map { it.string("key") }, script.fields.orEmpty().map { it.key }, "fields")
        fields.zip(script.fields.orEmpty()).forEach { (field, native) ->
            assertEquals(field.string("name"), native.name)
            assertEquals(field.string("description"), native.description)
            assertEquals(field["checkbox"] !is JsonNull && field["checkbox"] != null, native.optional, "${native.key} box")
            assertEquals(field.obj("checkbox")?.boolean("checked") ?: true, native.key in script.initialData || !native.optional, "${native.key} ticked")
            assertEquals(field["value"]?.takeUnless { it is JsonNull }, script.initialData[native.key], "${native.key} value")
        }
        val buttons = drawn.list("buttons").map { it.string("text") to !it.boolean("disabled")!! }
        assertEquals(buttons, listOf(script.cancel to script.cancelEnabled, script.run to script.canRun(script.initialData)))
    }

    @TestFactory
    fun `Given a script when running or cancelling it then the calls are the frontend's`() = controls.obj("entities")!!.filterKeys { it.startsWith("script.") }.map { (id, variants) ->
        DynamicTest.dynamicTest(id) {
            val captured = (variants as JsonObject).obj("as_is")!!
            val state = parseStates(JsonArray(listOf(captured.obj("stateObj")!!))).getValue(id)
            val script = (base.extra(state) as MoreInfoExtra.Script).script
            val calls = (captured["calls"] as JsonArray).associate { record ->
                record.jsonObject.string("control") to (record.jsonObject["calls"] as JsonArray).single().jsonObject
            }
            assertCall(calls.getValue("run"), script.runCall(script.initialData))
            assertCall(calls.getValue("cancel"), script.cancelCall())
        }
    }

    @TestFactory
    fun `Given a camera when deriving its details then the download button is the frontend's`() = perVariant("camera") { hass, state, drawn ->
        val camera = hass.extra(state) as MoreInfoExtra.Camera
        assertEquals(drawn.string("download"), camera.download)
        assertEquals(drawn.boolean("downloadDisabled"), !camera.downloadEnabled)
    }

    @TestFactory
    fun `Given a weather entity when deriving its details then the conditions and readings are the frontend's`() = perVariant("weather") { hass, state, drawn ->
        val weather = (hass.extra(state) as MoreInfoExtra.Weather).weather
        assertEquals(drawn.string("state"), weather.state)
        assertEquals(drawn.string("temp")?.replace(' ', ' '), weather.temperature?.replace(' ', ' '))
        assertEquals(drawn.list("rows").map { it.string("name") to it.string("value") }, weather.rows.map { it.name to it.value })
        assertEquals(drawn.string("section")?.replace(" :", ":"), weather.forecastTitle)
        assertEquals(drawn.list("tabs").map { it.string("label") }, weather.tabs.map { it.second })
    }

    private fun assertCall(captured: JsonObject, call: CardAction.CallService) {
        assertEquals(captured.string("domain"), call.domain)
        assertEquals(captured.string("service"), call.service)
        assertEquals(captured["data"], call.data)
    }

    private fun HassSnapshot.extra(state: EntityState) = moreInfoExtra(state, NOW)

    private fun perVariant(domain: String, check: (HassSnapshot, EntityState, JsonObject) -> Unit): List<DynamicTest> = controls.obj("entities")!!.filterKeys { it.startsWith("$domain.") }.flatMap { (id, variants) ->
        (variants as JsonObject).map { (name, captured) ->
            DynamicTest.dynamicTest("$id $name") {
                val stateObj = (captured as JsonObject).obj("stateObj")!!
                val states = parseStates(JsonArray(listOf(stateObj)))
                val drawn = captured.obj("main")!!.obj("details")
                assertNotNull(drawn, "captured details")
                check(base.copy(states = base.states + states), states.getValue(id), drawn!!)
            }
        }
    }

    private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().map { it.jsonObject }

    private fun JsonObject.strings(key: String): List<String?> = (this[key] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-11T00:00:00Z")
    }
}
