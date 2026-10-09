package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Differential test of the power sources graph against the frontend's. */
class PowerSourcesGraphGoldenTest {

    private val energy = EnergyFixture()

    @Test
    fun `Given today's data and the live states when computing the power sources graph then it has the frontend's series`() {
        // Only today: a new frontend card treats any period as today (it checks the chart's initial day)
        val recorded = energy.periods.first { it.name == "today" }
        val expected = recorded.card("power-sources-graph")
        val fixture = energy.fixture.hass
        val hass = fixture.copy(states = fixture.states + parseStates(JsonArray(expected.obj("states")!!.values.toList())))
        val expectedSeries = expected.objects("series")
        // The card appended the current states at the time it computed, the last point's
        val now = Instant.ofEpochMilli((expectedSeries.first().array("data")!!.last() as JsonArray)[0].let { (it as JsonPrimitive).double }.toLong())

        val graph = hass.powerSourcesGraph(recorded.data, now)

        assertEquals(expectedSeries.map { it.string("id") to it.string("name") }, graph.series.map { it.id to it.name })
        expectedSeries.zip(graph.series).forEach { (series, actual) ->
            val points = series.array("data")!!.map { point -> (point as JsonArray).map { (it as JsonPrimitive).double } }
            assertEquals(points.map { it[0].toLong() }, actual.points.map { it.x }, "${actual.id} times")
            points.zip(actual.points).forEach { (point, actualPoint) -> assertEquals(point[1], actualPoint.y, TOLERANCE, actual.id) }
        }
        assertEquals(expected.number("yAxisFractionDigits")?.toInt(), graph.yFractionDigits)
        assertEquals(Instant.parse(expected.string("xMin")).toEpochMilli(), graph.xMin)
        assertEquals(Instant.parse(expected.string("xMax")).toEpochMilli(), graph.xMax)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
