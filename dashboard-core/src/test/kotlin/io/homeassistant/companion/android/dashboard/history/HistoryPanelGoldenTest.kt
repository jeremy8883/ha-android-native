package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.energy.parseStatistics
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the History panel against what the frontend loaded, merged and drew on a phone
 * (20260624.6, tools/golden/capture.mjs --history-panel, history/panel.json): the requests, the history with the
 * statistics merged in, the line charts' series and legends, and the timelines.
 */
class HistoryPanelGoldenTest {

    private val fixture = GoldenFixture("test-instance")
    private val capture = fixture.json("history/panel.json")
    private val hass = fixture.hass.copy(states = parseStates(JsonArray(capture.obj("states")!!.values.toList())))
    private val entityIds = capture.array("entityIds")!!.mapNotNull { it.stringOrNull }
    private val start = Instant.parse(capture.array("range")!![0].stringOrNull)
    private val end = Instant.parse(capture.array("range")!![1].stringOrNull)

    // The page drew its charts within a second of the period's end (its "now"), so sensors end with their state
    private val now = end
    private val history = computed()
    private val expected = capture.obj("history")!!

    @Test
    fun `Given a period and entities when loading the history then the requests are the frontend's`() {
        // Without the connection's message ids
        val requests = capture.objects("requests").map { JsonObject(it - "id") }
        assertEquals(requests[0], hass.historyRangeCommand(entityIds, start, end).json())
        assertEquals(requests[1], historyPanelStatisticsCommand(entityIds, start, end).json())
    }

    @Test
    fun `Given the stream and statistics when merging them then the lines and timelines are the frontend's`() {
        assertEquals(expected.objects("line").map { it.lineSummary() }, history.line.map { it.summary() })
        assertEquals(expected.objects("timeline").map { it.timelineSummary() }, history.timeline.map { it.summary() })
    }

    @TestFactory
    fun `Given the merged history when drawing each line chart then its series and legend are the frontend's`(): List<DynamicTest> = capture.objects("lines").mapIndexed { index, drawn ->
        DynamicTest.dynamicTest("${drawn.string("unit")} ${drawn.string("identifier")}") {
            val unit = history.line[index]
            val chart = hass.historyLineChart(unit.data, end.toEpochMilli().toDouble(), now.toEpochMilli().toDouble(), showNames = true)
            assertEquals(drawn.string("unit"), unit.unit, "unit")
            assertEquals(
                drawn.objects("series").map { listOf(it.string("id"), it.string("name"), it.number("points")?.toInt(), it["fill"].toString()) },
                chart.series.map { listOf(it.id, it.name, it.points.size, it.fill.toString()) },
                "series",
            )
            assertEquals(drawn.number("yAxisFractionDigits")?.toInt(), chart.yFractionDigits, "y axis digits")
            assertEquals(
                drawn.objects("legend").map { listOf(it.string("id"), it.string("name"), it.string("value")) },
                hass.historyLegend(chart).map { listOf(it.id, it.name, it.value) },
                "legend",
            )
        }
    }

    @Test
    fun `Given the merged history when drawing the timelines then each row has the frontend's name and bands`() {
        val rows = capture.objects("timelines").flatMap { it.objects("rows") }
        val charts = history.timeline.map { hass.timelineChart(it, end.toEpochMilli().toDouble()) }
        assertEquals(
            rows.map { listOf(it.string("id"), it.string("name"), it.number("bands")?.toInt()) },
            charts.map { listOf(it.entityId, it.name, it.bands.size) },
        )
    }

    private fun computed(): HistoryResult {
        val states = capture.objects("messages").fold(emptyMap<String, List<HistoryState>>()) { acc, message ->
            acc.merge(parseHistoryStates(message.obj("states")!!), null)
        }
        val statistics = capture.obj("statistics")?.let(::parseStatistics)
        val lts = statistics?.let { hass.convertStatisticsToHistory(it, entityIds, splitDeviceClasses = true) }
        return mergeHistoryResults(hass.computeHistory(states, entityIds, splitDeviceClasses = true), lts)
    }

    private fun io.homeassistant.companion.android.dashboard.energy.WsCommand.json() = JsonObject(mapOf("type" to JsonPrimitive(type)) + params)

    private fun JsonObject.lineSummary() = listOf(
        string("unit"),
        string("device_class"),
        string("identifier"),
        objects("data").map { entity ->
            listOf(
                entity.string("entity_id"),
                entity.string("name"),
                entity.objects("states").map { listOf(it.string("state"), it.number("last_changed")?.toLong()) },
                entity.array("statistics")?.map { (it as JsonObject).let { s -> listOf(s.string("state"), s.number("last_changed")?.toLong()) } },
            )
        },
    )

    private fun LineChartUnit.summary() = listOf(
        unit,
        deviceClass,
        identifier,
        data.map { entity ->
            listOf(
                entity.entityId,
                entity.name,
                entity.states.map { listOf(it.state, it.lastChanged) },
                entity.statistics?.map { listOf(it.state, it.lastChanged) },
            )
        },
    )

    private fun JsonObject.timelineSummary() = listOf(
        string("entity_id"),
        string("name"),
        objects("data").map { listOf(it.string("state"), it.string("state_localize"), it.number("last_changed")?.toLong()) },
    )

    private fun TimelineEntity.summary() = listOf(
        entityId,
        name,
        data.map { listOf(it.state, it.stateLocalize, it.lastChanged) },
    )
}
