package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the energy usage graph's bars against the series the real frontend's chart had. */
class EnergyUsageGraphGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the usage graph then it has the frontend's bars`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val expected = recorded.card("energy-usage-graph")
            // The empty series that puts the compared bars first is ECharts' business
            val expectedSeries = expected.objects("series").filter { it.string("id") != "compare-placeholder" }

            val model = energy.fixture.hass.energyUsageGraph(recorded.data)

            assertEquals(expectedSeries.map { it.string("id") }, model.chart.series.map { it.id })
            assertEquals(expectedSeries.map { it.string("name") }, model.chart.series.map { it.name })
            assertEquals(expectedSeries.map { it.string("stack") }, model.chart.series.map { if (it.compare) "compare" else "usage" })
            expectedSeries.zip(model.chart.series).forEach { (series, actual) ->
                // Gap-filled bars ([x, 0], without a start) are added for ECharts' stacking only
                val points = (series["data"] as JsonArray).map { it as JsonArray }.filter { it.size == 3 }
                assertEquals(points.map { it[0].jsonPrimitive.long }, actual.points.map { it.x }, "${actual.id} x")
                assertEquals(points.map { it[2].jsonPrimitive.long }, actual.points.map { it.start }, "${actual.id} start")
                points.zip(actual.points).forEach { (point, bar) ->
                    assertEquals(point[1].jsonPrimitive.double, bar.y, TOLERANCE, "${actual.id} y")
                }
            }
            assertEquals(expected.number("total"), model.total)
            assertEquals(expected.number("yAxisFractionDigits")?.toInt(), model.chart.yFractionDigits)
            assertEquals(Instant.parse(expected.string("xMin")).toEpochMilli(), model.chart.xMin)
            assertEquals(Instant.parse(expected.string("xMax")).toEpochMilli(), model.chart.xMax)
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
