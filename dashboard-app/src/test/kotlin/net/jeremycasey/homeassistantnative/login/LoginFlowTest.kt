package net.jeremycasey.homeassistantnative.login

import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoginFlowTest {

    private fun step(json: String) = parseStep(Json.parseToJsonElement(json))

    @Test
    fun `Given the password form when parsed then it has its fields and the error of the last try`() {
        val form = step(
            """{"type":"form","flow_id":"f1","handler":["homeassistant",null],"data_schema":[
              {"type":"string","name":"username","required":true},{"type":"string","name":"password","required":true}],
              "errors":{"base":"invalid_auth"},"description_placeholders":null,"step_id":"init"}""",
        )
        assertEquals(
            Fetched.Success(
                LoginStep.Form(
                    flowId = "f1",
                    handlerType = "homeassistant",
                    stepId = "init",
                    fields = listOf(
                        LoginField("username", required = true, type = LoginFieldType.Text),
                        LoginField("password", required = true, type = LoginFieldType.Text),
                    ),
                    errors = mapOf("base" to "invalid_auth"),
                    placeholders = emptyMap(),
                ),
            ),
            form,
        )
    }

    @Test
    fun `Given the trusted networks user picker when parsed then its users are the options`() {
        val form = step(
            """{"type":"form","flow_id":"f2","handler":["trusted_networks",null],"step_id":"init","errors":{},
              "data_schema":[{"type":"select","name":"user","required":true,"options":[["u1","Ann"],["u2","Bob"]]}]}""",
        ) as Fetched.Success
        val field = (form.value as LoginStep.Form).fields.single()
        assertEquals(LoginFieldType.Select(listOf("u1" to "Ann", "u2" to "Bob")), field.type)
    }

    @Test
    fun `Given a field of a kind the form can't show when parsed then the step is unexpected`() {
        val form = step(
            """{"type":"form","flow_id":"f3","handler":["custom",null],"step_id":"init","errors":{},
              "data_schema":[{"type":"constant","name":"note"}]}""",
        )
        assertEquals(Fetched.Failure(LoadError.UnexpectedResponse("login form")), form)
    }

    @Test
    fun `Given the flow is done or aborted when parsed then it ends with the code or the reason`() {
        assertEquals(
            Fetched.Success(LoginStep.Done("code1")),
            step("""{"type":"create_entry","flow_id":"f1","handler":["homeassistant",null],"result":"code1"}"""),
        )
        assertEquals(
            Fetched.Success(LoginStep.Aborted("homeassistant", "login_expired")),
            step("""{"type":"abort","flow_id":"f1","handler":["homeassistant",null],"reason":"login_expired"}"""),
        )
    }

    @Test
    fun `Given addresses typed in when read then they become the server's base URL`() {
        assertEquals("http://homeassistant.local:8123/", serverAddress(" homeassistant.local:8123 ").toString())
        assertEquals("https://ha.example.com/", serverAddress("https://ha.example.com/lovelace/0?x=1").toString())
        assertNull(serverAddress(""))
        assertNull(serverAddress("http://"))
    }
}
