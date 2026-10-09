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

/** Differential tests of the gas, water and solar graphs' bars against the series the real frontend's charts had. */
class EnergySourceGraphGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing a source graph then it has the frontend's bars`(): List<DynamicTest> = energy.periods.flatMap { recorded ->
        SourceGraphKind.entries.map { kind ->
            DynamicTest.dynamicTest("${recorded.name} ${kind.kind}") {
                val expected = recorded.card("energy-${kind.kind}-graph")
                // The empty series that puts the compared bars first is ECharts' business
                val expectedSeries = expected.objects("series").filter { it.string("name") != null }

                val model = energy.fixture.hass.energySourceGraph(recorded.data, kind)

                assertEquals(expectedSeries.map { it.string("id") }, model.chart.series.map { it.id })
                assertEquals(expectedSeries.map { it.string("name") }, model.chart.series.map { it.name })
                expectedSeries.zip(model.chart.series).forEach { (series, actual) ->
                    // Gap-filled bars ([x, 0], without a start) are added for ECharts' stacking only
                    val points = (series["data"] as JsonArray).map { it as JsonArray }.filter { it.size == 3 }
                    assertEquals(points.map { it[0].jsonPrimitive.long }, actual.points.map { it.x }, "${actual.id} x")
                    assertEquals(points.map { it[2].jsonPrimitive.long }, actual.points.map { it.start }, "${actual.id} start")
                    assertEquals(points.map { it[1].jsonPrimitive.double }, actual.points.map { it.y }, "${actual.id} y")
                }
                assertEquals(expected.number("total")!!, model.total, TOLERANCE)
                assertEquals(expected.number("yAxisFractionDigits")?.toInt(), model.chart.yFractionDigits)
                assertEquals(Instant.parse(expected.string("xMin")).toEpochMilli(), model.chart.xMin)
                assertEquals(Instant.parse(expected.string("xMax")).toEpochMilli(), model.chart.xMax)
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
