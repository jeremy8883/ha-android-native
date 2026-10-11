package io.homeassistant.companion.android.dashboard.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Checks against js-yaml@4.1.1 `dump(value, { quotingType: '"', noRefs: true })` and `JSON.stringify(value, null, 2)`
 * run in Node on the same values (fixtures/js-serialization/js-yaml-4.1.1-dump.json).
 */
class JsSerializationTest {
    private val cases: JsonArray = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream(FIXTURE)).reader().readText(),
    ).jsonArray

    @TestFactory
    fun `Given values when dumping them as YAML then the text is js-yaml's`(): List<DynamicTest> = cases.mapIndexed { index, case ->
        DynamicTest.dynamicTest("case $index") {
            val input = Json.parseToJsonElement(case.jsonObject.getValue("input").jsonPrimitive.content)
            assertEquals(case.jsonObject.getValue("yaml").jsonPrimitive.content, yamlDump(input))
        }
    }

    @TestFactory
    fun `Given values when stringifying them then the text is JSON stringify's`(): List<DynamicTest> = cases.mapIndexed { index, case ->
        DynamicTest.dynamicTest("case $index") {
            val input = Json.parseToJsonElement(case.jsonObject.getValue("input").jsonPrimitive.content)
            assertEquals(case.jsonObject.getValue("json").jsonPrimitive.content, jsonStringify(input))
        }
    }

    @Test
    fun `Given numbers when formatting them then they read as in JavaScript`() {
        assertEquals("1e+21", jsNumberString(1e21))
        assertEquals("100000000000000000000", jsNumberString(1e20))
        assertEquals("1e-7", jsNumberString(1e-7))
        assertEquals("0.000001", jsNumberString(1e-6))
        assertEquals("-1.5", jsNumberString(-1.5))
        assertEquals("0.30000000000000004", jsNumberString(0.1 + 0.2))
    }

    private companion object {
        const val FIXTURE = "/fixtures/js-serialization/js-yaml-4.1.1-dump.json"
    }
}
