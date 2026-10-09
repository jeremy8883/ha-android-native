package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.shadeRgb
import io.homeassistant.companion.android.dashboard.energy.parseStatistics
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.theme.themeColorArgb
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the more-info history charts' series against the frontend's ECharts series. */
class HistoryChartsGoldenTest {

    private val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
    private val recorded = fixture.json("history/more-info.json").obj("entities")!!

    @TestFactory
    fun `Given an entity's history when drawing its charts then they have the frontend's series`(): List<DynamicTest> = recorded.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val hass = entity.hass()
            entity.objects("charts").forEach { chart ->
                when (chart.string("tag")) {
                    "state-history-chart-timeline" -> assertTimeline(hass, entity, chart)
                    "state-history-chart-line" -> assertLines(hass, entity, chart)
                    "statistics-chart" -> assertStatistics(hass, entityId, entity, chart)
                }
            }
        }
    }

    private fun assertTimeline(hass: HassSnapshot, entity: JsonObject, chart: JsonObject) {
        val end = chart.number("endTime")!!
        val result = hass.computeHistory(entity.history(), listOf(entity.entityId()))
        val timelines = result.timeline.map { hass.timelineChart(it, end) }
        val expected = chart.objects("series")
        assertEquals(expected.map { it.string("id") }, timelines.map { it.entityId })
        expected.zip(timelines).forEach { (series, actual) ->
            val bands = series.points()
            assertEquals(bands.map { it[3].text() }, actual.bands.map { it.label }, "labels")
            assertTimes(bands.map { it[1].num()!! }, actual.bands.map { it.start })
            assertTimes(bands.map { it[2].num()!! }, actual.bands.map { it.end })
            bands.zip(actual.bands).forEach { (band, actualBand) ->
                val color = resolve(actualBand.color)
                if (color == null) {
                    // A state's own colour, picked from the palette in the order states first showed in the page
                    assertTrue(band[4].text() in PALETTE, "${actualBand.state}: ${band[4].text()} is a palette colour")
                } else {
                    assertEquals(band[4].text(), color, actualBand.state)
                }
            }
        }
    }

    private fun assertLines(hass: HassSnapshot, entity: JsonObject, chart: JsonObject) {
        val end = chart.number("endTime")!!
        val result = hass.computeHistory(entity.history(), listOf(entity.entityId()))
        val lines = result.line.flatMap { hass.historyLineChart(it.data, end, end).series }
        val expected = chart.objects("series")
        assertEquals(expected.map { it.string("id") to it.string("name") }, lines.map { it.id to it.name })
        expected.zip(lines).forEach { (series, actual) ->
            assertEquals(series.string("color"), hex(actual.color), "${actual.id} colour")
            assertEquals(series.number("lineWidth") == 0.0, actual.fill, "${actual.id} fill")
            val points = series.points()
            assertTimes(points.map { it[0].num()!! }, actual.points.map { it.x })
            assertEquals(points.map { it[1].num() }, actual.points.map { it.y }, "${actual.id} values")
        }
        assertEquals(chart.number("yAxisFractionDigits")?.toInt(), hass.historyLineChart(result.line.single().data, end, end).yFractionDigits)
    }

    private fun assertStatistics(hass: HassSnapshot, entityId: String, entity: JsonObject, chart: JsonObject) {
        val statistics = entity.obj("statistics")!!
        val stats = parseStatistics(statistics).getValue(entityId)
        val expected = chart.objects("series")
        // The chart ends with the current state at the time it drew, or at the last period's end
        val now = expected.first().points().last()[0].num()!!
        val model = hass.statisticsChart(entityId, stats, null, now)
        assertEquals(expected.map { it.string("id") to it.string("name") }, model.series.map { it.id to it.name })
        expected.zip(model.series).forEach { (series, actual) ->
            val points = series.points()
            assertEquals(points.map { it[0].num() }, actual.points.map { it.x }, "${actual.id} times")
            if (actual.bandTop) {
                assertValues(points.map { it[1].num() }, actual.points.map { it.height }, actual.id)
                assertValues(points.map { it.getOrNull(2)?.num() }, actual.points.map { it.value.takeIf { _ -> it.height != null } }, actual.id)
            } else {
                assertValues(points.map { it[1].num() }, actual.points.map { it.value }, actual.id)
            }
            val color = PALETTE.first() + if (actual.hidden) "00" else ""
            assertEquals(series.string("color"), color, "${actual.id} colour")
            assertEquals(series.string("area") != null, actual.bandTop, "${actual.id} area")
        }
        assertEquals(chart.number("yAxisFractionDigits")?.toInt(), model.yFractionDigits)
    }

    private fun JsonObject.entityId() = obj("state")!!.string("entity_id")!!

    private fun JsonObject.hass(): HassSnapshot {
        val state = this["state"] as? JsonObject
        return fixture.hass.copy(states = fixture.hass.states + state?.let { parseStates(JsonArray(listOf(it))) }.orEmpty())
    }

    private fun JsonObject.history(): HistoryStates = objects("messages").filter { "states" in it }.fold(emptyMap()) { acc, message ->
        acc.merge(parseHistoryStates(message.obj("states")!!), message.number("receivedAt")!! / MILLIS - DAY_SECONDS)
    }

    private fun JsonObject.points(): List<List<JsonPrimitive>> = (this["data"] as JsonArray).map { point -> (point as JsonArray).map { it as JsonPrimitive } }

    private fun JsonPrimitive.num(): Double? = doubleOrNull

    private fun JsonPrimitive.text(): String = content

    private fun resolve(color: TimelineColor): String? {
        color.variables.firstNotNullOfOrNull { themeColorArgb(it, dark = false) }?.let { argb ->
            val rgb = (argb and RGB).toInt()
            return hex(color.shade?.let { shadeRgb(rgb, it, brighten = true) } ?: rgb)
        }
        return color.paletteIndex?.let { PALETTE[it % PALETTE.size] }
    }

    private fun hex(color: SeriesColor): String? = when (color) {
        is SeriesColor.Palette -> PALETTE[color.index % PALETTE.size]
        is SeriesColor.Variable -> themeColorArgb(color.name, dark = false)?.let { hex((it and RGB).toInt()) }
    }

    private fun hex(rgb: Int) = "#%06x".format(rgb)

    private fun assertTimes(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertTrue(abs(e - a) < 1, "time $a, expected $e") }
    }

    private fun assertValues(expected: List<Double?>, actual: List<Double?>, id: String) {
        assertEquals(expected.size, actual.size, id)
        expected.zip(actual).forEach { (e, a) ->
            if (e == null || a == null) assertEquals(e, a, id) else assertEquals(e, a, TOLERANCE, id)
        }
    }

    private companion object {
        const val MILLIS = 1000.0
        const val DAY_SECONDS = 24 * 60 * 60.0
        const val TOLERANCE = 1e-9
        const val RGB = 0xFFFFFFL

        /** The graph palette, `--color-1`... */
        val PALETTE = (1..54).mapNotNull { i -> themeColorArgb("color-$i", dark = false)?.let { "#%06x".format((it and RGB).toInt()) } }
    }
}
