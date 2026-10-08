package io.homeassistant.companion.android.dashboard.condition

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.states
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConditionsTest {
    private val hass = hass(
        states = states(
            """
            {
              "sensor.test": {"s": "on", "a": {"level": 7, "mode": "eco", "nothing": null}},
              "sensor.number": {"s": "5", "a": {}},
              "sensor.threshold": {"s": "3", "a": {}},
              "input_select.target": {"s": "eco", "a": {}},
              "person.dev": {"s": "home", "a": {"user_id": "u1"}},
              "person.other": {"s": "work", "a": {"user_id": "u2"}}
            }
            """,
        ),
    )

    private fun met(conditions: String, context: ConditionContext = ConditionContext()): Boolean = hass.conditionsMet(Json.parseToJsonElement(conditions).jsonArray.map { it.jsonObject }, context)

    /** Ports of frontend@20260624.6 test/panels/lovelace/common/validate-condition.test.ts `checkConditionsMet`. */
    @Nested
    inner class Upstream {
        @Test
        fun `Given state condition when state matches then met`() {
            assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "state": "on"}]"""))
        }

        @Test
        fun `Given state condition when state differs then not met`() {
            assertFalse(met("""[{"condition": "state", "entity": "sensor.test", "state": "off"}]"""))
        }

        @Test
        fun `Given state condition without state or state_not then not met`() {
            assertFalse(met("""[{"condition": "state", "entity": "sensor.test"}]"""))
        }

        @Test
        fun `Given invalid condition type then it is evaluated as a state condition without crashing`() {
            assertFalse(met("""[{"condition": "numeric", "entity": "sensor.number", "above": 0}]"""))
        }

        @Test
        fun `Given numeric_state above threshold then met`() {
            assertTrue(met("""[{"condition": "numeric_state", "entity": "sensor.number", "above": 0}]"""))
        }

        @Test
        fun `Given legacy state condition then it is evaluated`() {
            assertTrue(met("""[{"entity": "sensor.test", "state": "on"}]"""))
            assertFalse(met("""[{"entity": "sensor.test"}]"""))
        }
    }

    @Test
    fun `Given state_not, lists and attributes when evaluated then they match upstream semantics`() {
        assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "state_not": "off"}]"""))
        assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "state": ["off", "on"]}]"""))
        assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "attribute": "level", "state": "7"}]"""))
        // Null or missing attributes read as "unknown", as do missing entities
        assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "attribute": "nothing", "state": "unknown"}]"""))
        assertTrue(met("""[{"condition": "state", "entity": "sensor.missing", "state": "unknown"}]"""))
    }

    @Test
    fun `Given state value naming an entity when evaluated then that entity's state also matches`() {
        assertTrue(met("""[{"condition": "state", "entity": "sensor.test", "attribute": "mode", "state": "input_select.target"}]"""))
    }

    @Test
    fun `Given condition without entity when evaluated then the context entity is used`() {
        assertTrue(met("""[{"condition": "state", "state": "5"}]""", ConditionContext(entityId = "sensor.number")))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            """{"above": 4} | true""",
            """{"above": 5} | false""",
            """{"below": 6} | true""",
            """{"above": 4, "below": 5} | false""",
            """{"above": "sensor.threshold"} | true""",
            """{"below": "sensor.threshold"} | false""",
            """{"above": "not a number"} | true""",
            """{"entity": "sensor.test", "attribute": "level", "above": 6} | true""",
        ],
    )
    fun `Given numeric_state bounds when evaluated then they match upstream semantics`(bounds: String, expected: Boolean) {
        val condition = JsonObject(json("""{"condition": "numeric_state", "entity": "sensor.number"}""") + json(bounds))
        assertEquals(expected, hass.conditionsMet(listOf(condition), ConditionContext()))
    }

    @Test
    fun `Given numeric_state on a non-numeric state then not met`() {
        assertFalse(met("""[{"condition": "numeric_state", "entity": "sensor.test", "above": 0}]"""))
    }

    @Test
    fun `Given and, or and not when evaluated then they combine, and an empty one is met`() {
        val on = """{"condition": "state", "entity": "sensor.test", "state": "on"}"""
        val off = """{"condition": "state", "entity": "sensor.test", "state": "off"}"""
        assertTrue(met("""[{"condition": "or", "conditions": [$off, $on]}]"""))
        assertFalse(met("""[{"condition": "and", "conditions": [$off, $on]}]"""))
        assertTrue(met("""[{"condition": "not", "conditions": [$off]}]"""))
        assertTrue(met("""[{"condition": "or"}]"""))
    }

    @Test
    fun `Given user and location conditions when evaluated then they use the current user and their person`() {
        assertTrue(met("""[{"condition": "user", "users": ["u1"]}]"""))
        assertFalse(met("""[{"condition": "user", "users": ["u2"]}]"""))
        assertTrue(met("""[{"condition": "location", "locations": ["home"]}]"""))
        assertFalse(met("""[{"condition": "location", "locations": ["work"]}]"""))
    }

    @Test
    fun `Given view_columns condition when evaluated then it compares the view column count`() {
        val large = """[{"condition": "view_columns", "min": 2}]"""
        val small = """[{"condition": "view_columns", "max": 1}]"""
        assertFalse(met(large, ConditionContext(maxColumns = 1)))
        assertTrue(met(small, ConditionContext(maxColumns = 1)))
        assertTrue(met(large, ConditionContext(maxColumns = 3)))
        // Before the view knows its columns, upstream treats it as met
        assertTrue(met(large))
    }

    @Test
    fun `Given screen condition when evaluated then the media query is matched against the window`() {
        val phone = ConditionContext(screen = ScreenInfo(widthDp = 412, heightDp = 915))
        assertTrue(met("""[{"condition": "screen", "media_query": "(min-width: 0px) and (max-width: 767px)"}]""", phone))
        assertFalse(met("""[{"condition": "screen", "media_query": "(min-width: 1024px)"}]""", phone))
        assertTrue(met("""[{"condition": "screen", "media_query": "(orientation: portrait)"}]""", phone))
        assertFalse(met("""[{"condition": "screen", "media_query": "(min-width: 1024px)"}]"""))
    }

    /** Ports of frontend@20260624.6 test/common/datetime/check_time.test.ts `checkTimeInRange`. */
    @ParameterizedTest(name = "{0} at {1} -> {2}")
    @CsvSource(
        delimiter = '|',
        value = [
            """{"after": "08:00", "before": "17:00"} | 10:00 | true""",
            """{"after": "08:00", "before": "17:00"} | 07:00 | false""",
            """{"after": "08:00", "before": "17:00"} | 18:00 | false""",
            """{"after": "22:00", "before": "06:00"} | 23:00 | true""",
            """{"after": "22:00", "before": "06:00"} | 22:00 | true""",
            """{"after": "22:00", "before": "06:00"} | 03:00 | true""",
            """{"after": "22:00", "before": "06:00"} | 06:00 | true""",
            """{"after": "22:00", "before": "06:00"} | 10:00 | false""",
            """{"after": "08:00"} | 10:00 | true""",
            """{"after": "08:00"} | 06:00 | false""",
            """{"before": "17:00"} | 10:00 | true""",
            """{"before": "17:00"} | 18:00 | false""",
            """{"weekdays": ["mon"]} | 10:00 | true""",
            """{"weekdays": ["tue"]} | 10:00 | false""",
            """{"weekdays": ["mon", "wed", "fri"]} | 10:00 | true""",
        ],
    )
    fun `Given time condition when evaluated then it matches upstream checkTimeInRange`(range: String, time: String, expected: Boolean) {
        // 2024-01-15 is a Monday, as in the upstream tests
        val (hours, minutes) = time.split(':').map(String::toInt)
        val now = ZonedDateTime.of(2024, 1, 15, hours, minutes, 0, 0, ZoneId.of("America/Los_Angeles"))
        val condition = JsonObject(json("""{"condition": "time"}""") + json(range))
        assertEquals(expected, hass.conditionsMet(listOf(condition), ConditionContext(now = now)))
    }

    @ParameterizedTest(name = "{0}dp, max {1} -> {2}")
    @CsvSource("412, 3, 1", "600, 3, 1", "700, 3, 2", "800, 3, 2", "1200, 3, 3", "1200, , 3", "1600, , 4", "0, 3, 1")
    fun `Given window width when computing sections view columns then upstream formula applies`(width: Int, max: Int?, expected: Int) {
        assertEquals(expected, sectionsViewColumns(width, max))
    }
}
