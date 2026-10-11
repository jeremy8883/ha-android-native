package io.homeassistant.companion.android.dashboard.flow

import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigFlowTest {

    /** The first step of `local_todo`'s flow, as the test instance answers it. */
    private val form = parseFlowStep(
        json(
            """{"type":"form","flow_id":"f1","handler":"local_todo","step_id":"user","errors":{},
            "data_schema":[{"type":"string","name":"todo_list_name","required":true}],
            "description_placeholders":null,"last_step":null}""",
        ),
    ) as FlowStep.Form

    /** `local_todo`'s config translations (`frontend/get_translations`) and the dialog's own strings. */
    private val localize = Localize { key ->
        mapOf(
            "component.local_todo.config.step.user.data.todo_list_name" to "To-do list name",
            "component.local_todo.config.step.user.description" to "Please choose a name for your new To-do list",
            "component.local_todo.config.step.user.submit" to "Create",
            "component.local_todo.config.abort.already_configured" to "Service is already configured",
            "component.local_todo.title" to "Local To-do",
            "$FLOW.submit" to "Submit",
            "$FLOW.created_config" to "Created configuration for {name}.",
        )[key].orEmpty()
    }

    @Test
    fun `Given local to-do's first step when showing it then it reads as the frontend's dialog`() {
        val texts = localize.formTexts(form)

        assertEquals("Local To-do", texts.title)
        assertEquals("Please choose a name for your new To-do list", texts.description)
        assertEquals(mapOf("todo_list_name" to "To-do list name"), texts.labels)
        assertEquals("Create", texts.submit)
        assertEquals(listOf(FlowField("todo_list_name", true, FlowFieldType.Text(), null)), form.fields)
    }

    @Test
    fun `Given a form when submitting it then blank required fields stop it and the others are sent as typed`() {
        assertNull(form.values(mapOf("todo_list_name" to "  ")))
        assertEquals(JsonObject(mapOf("todo_list_name" to JsonPrimitive("Groceries"))), form.values(mapOf("todo_list_name" to "Groceries ")))
    }

    @Test
    fun `Given a refused form when showing it then its errors read as the integration says them`() {
        val refused = form.copy(errors = mapOf("base" to "unknown"))

        // Without a translation, the error's key, as upstream
        assertEquals(mapOf("base" to "unknown"), localize.formTexts(refused).errors)
    }

    @Test
    fun `Given the entry created when finishing then it says the config was created, named`() {
        val created = parseFlowStep(
            json("""{"type":"create_entry","flow_id":"f1","handler":"local_todo","title":"Groceries","result":{"entry_id":"e1","state":"loaded"}}"""),
        ) as FlowStep.CreateEntry

        assertEquals("e1", created.entryId)
        assertEquals(listOf("Created configuration for Groceries."), localize.createdTexts(created))
    }

    @Test
    fun `Given an aborted flow when showing it then it says why`() {
        val aborted = parseFlowStep(
            json("""{"type":"abort","flow_id":"f1","handler":"local_todo","reason":"already_configured"}"""),
        ) as FlowStep.Abort

        assertEquals("Service is already configured", localize.abortText(aborted))
    }

    @Test
    fun `Given fields of other kinds when reading a form then numbers, switches and choices are kept`() {
        val step = parseFlowStep(
            json(
                """{"type":"form","flow_id":"f","handler":"x","step_id":"user","data_schema":[
                {"type":"integer","name":"port","default":8080},
                {"type":"boolean","name":"ssl"},
                {"name":"mode","selector":{"select":{"options":[{"value":"a","label":"A"},"b"]}}},
                {"name":"area","selector":{"area":{}}}]}""",
            ),
        ) as FlowStep.Form

        assertEquals(listOf("port", "ssl", "mode"), step.fields.map { it.name })
        assertEquals(FlowFieldType.Select(listOf("a" to "A", "b" to "b")), step.fields[2].type)
        assertEquals(mapOf("port" to "8080"), step.initialTexts())
        assertEquals(JsonObject(mapOf("port" to JsonPrimitive(443L), "ssl" to JsonPrimitive(true))), step.values(mapOf("port" to "443", "ssl" to "true")))
    }
}
