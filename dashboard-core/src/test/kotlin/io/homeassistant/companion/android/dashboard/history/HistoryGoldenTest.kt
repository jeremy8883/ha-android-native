package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the history the more-info dialog computes from the stream, against the frontend's. */
class HistoryGoldenTest {

    private val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
    private val recorded = fixture.json("history/more-info.json").obj("entities")!!

    @TestFactory
    fun `Given an entity's history stream when computing its history then it has the frontend's lines and timelines`(): List<DynamicTest> = recorded.filterValues { (it as JsonObject)["stateHistory"] is JsonObject }.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val expected = entity.obj("stateHistory")!!
            val state = entity["state"] as? JsonObject
            val hass = fixture.hass.copy(
                states = fixture.hass.states + state?.let { parseStates(JsonArray(listOf(it))) }.orEmpty(),
            )
            // The stream's messages, each merged as it came, old states purged 24 hours back from then
            val history = entity.objects("messages").filter { "states" in it }.fold(emptyMap<String, List<HistoryState>>()) { acc, message ->
                acc.merge(parseHistoryStates(message.obj("states")!!), message.number("receivedAt")!! / MILLIS - DAY_SECONDS)
            }

            val result = hass.computeHistory(history, listOf(entityId))

            val timeline = expected.objects("timeline")
            assertEquals(timeline.map { it.string("entity_id") to it.string("name") }, result.timeline.map { it.entityId to it.name })
            timeline.zip(result.timeline).forEach { (band, actual) ->
                val data = band.objects("data")
                assertEquals(data.map { it.string("state") to it.string("state_localize") }, actual.data.map { it.state to it.stateLocalize })
                assertTimes(data.map { it.number("last_changed")!! }, actual.data.map { it.lastChanged })
            }
            val lines = expected.objects("line")
            assertEquals(lines.map { it.string("unit") to it.string("identifier") }, result.line.map { it.unit to it.identifier })
            lines.zip(result.line).forEach { (unit, actual) ->
                val data = unit.objects("data")
                assertEquals(data.map { it.string("entity_id") to it.string("name") }, actual.data.map { it.entityId to it.name })
                data.zip(actual.data).forEach { (line, actualLine) ->
                    val states = line.objects("states")
                    assertEquals(states.map { it.string("state") }, actualLine.states.map { it.state })
                    assertEquals(states.map { it.obj("attributes") ?: JsonObject(emptyMap()) }, actualLine.states.map { it.attributes })
                    assertTimes(states.map { it.number("last_changed")!! }, actualLine.states.map { it.lastChanged })
                }
            }
        }
    }

    @TestFactory
    fun `Given an entity when loading its history then it sends the frontend's requests`(): List<DynamicTest> = recorded.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val state = entity["state"] as? JsonObject
            val hass = fixture.hass.copy(
                states = fixture.hass.states + state?.let { parseStates(JsonArray(listOf(it))) }.orEmpty(),
            )
            val now = Instant.EPOCH
            val expected = entity.objects("requests").filter { it.string("type") in HISTORY_COMMANDS }.map { it.withoutStart() }

            val usesStatistics = hass.historyUsesStatistics(entityId)
            val commands = if (usesStatistics) {
                listOf(statisticsMetadataCommand(entityId), historyStatisticsCommand(entityId, now))
            } else {
                listOf(historyStreamCommand(entityId, hass.historyWithoutAttributes(entityId), now))
            }

            assertTrue(hass.showsHistory(entityId))
            assertEquals(expected, commands.map { JsonObject(mapOf("type" to JsonPrimitive(it.type)) + it.params).withoutStart() })
        }
    }

    private fun JsonObject.withoutStart() = JsonObject(filterKeys { it != "start_time" })

    /** JavaScript keeps fractions of milliseconds; the app's times are whole ones. */
    private fun assertTimes(expected: List<Double>, actual: List<Long>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertTrue(abs(e - a) < 1, "time $a, expected $e") }
    }

    private companion object {
        const val MILLIS = 1000.0
        const val DAY_SECONDS = 24 * 60 * 60.0
        val HISTORY_COMMANDS = setOf("history/stream", "recorder/get_statistics_metadata", "recorder/statistics_during_period")
    }
}
