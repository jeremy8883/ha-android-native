package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The History charts' time axes against the frontend's (20260624.6 with ECharts 6.1.0, tools/golden/capture.mjs
 * --history-axes, history/axes.json): over 3 hours, 26 hours, 7 and 30 days, then zoomed to 40-60%, each chart's
 * ticks, which labels show (from the web's label positions and widths) and what they say. The page ran in UTC.
 */
class TimeAxisGoldenTest {
    private val capture = GoldenFixture("test-instance").json("history/axes.json")
    private val formats = JdkDisplayFormats(Locale.US, ZoneOffset.UTC)

    @TestFactory
    fun `Given a History chart's axis when placing its ticks and labels then they are the web's`(): List<DynamicTest> = capture.obj("periods")!!.flatMap { (period, value) ->
        listOf("full", "zoomed").flatMap { state ->
            (value as JsonObject).objects(state).mapIndexed { index, chart ->
                DynamicTest.dynamicTest("$period $state ${chart.string("kind")} $index") { assertAxis(chart, zoomed = state == "zoomed") }
            }
        }
    }

    private fun assertAxis(chart: JsonObject, zoomed: Boolean) {
        val (start, end) = chart.array("extent")!!.map { it.jsonPrimitive.long }
        val fullSpan = chart.number("axisMax")!!.toLong() - chart.number("axisMin")!!.toLong()
        val ticks = timeAxisTicks(start, end, ZoneOffset.UTC, fullSpan)
        val expected = chart.objects("ticks").map {
            TimeTick(it.number("value")!!.toLong(), it.number("level")!!.toInt(), nice = it["notNice"].toString() != "true")
        }
        assertEquals(expected, ticks, "ticks")

        val labels = chart.objects("labels")
        assertEquals(ticks.map { it.time }, labels.map { it.number("value")!!.toLong() }, "a label per tick")
        val boxes = labels.mapIndexed { i, label -> TimeAxisLabelBox(ticks[i], label.number("x")!!.toFloat(), label.number("width")!!.toFloat()) }
        assertEquals(labels.map { it["ignore"] != JsonPrimitive(true) }, visibleTimeLabels(boxes, TEXT_MARGIN), "shown labels")

        // The web's timelines keep the labels they had before zooming, so only the lines say the zoomed format
        if (zoomed && chart.string("kind") == "timeline") return
        val ratio = chart.number("zoomRatio")!!
        labels.zip(ticks).forEach { (label, tick) ->
            val text = label.string("text")!!
            val bold = text.startsWith(BOLD)
            val expectedLabel = TimeAxisLabel(if (bold) text.removePrefix(BOLD).removeSuffix("}") else text, bold)
            assertEquals(expectedLabel, formats.historyTimeLabel(tick.time, (fullSpan * ratio).toLong()), "label at ${tick.time}")
        }
    }

    private companion object {
        /** ECharts' default `axisLabel.textMargin` on the sides, px. */
        const val TEXT_MARGIN = 3f
        const val BOLD = "{bold|"
    }
}
