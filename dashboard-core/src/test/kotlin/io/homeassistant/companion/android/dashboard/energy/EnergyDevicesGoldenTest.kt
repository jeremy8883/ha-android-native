package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Differential tests of the device cards against what the real frontend's cards had. */
class EnergyDevicesGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period's data when computing the devices detail graph then it has the frontend's bars`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val expected = recorded.card("energy-devices-detail-graph")
            val expectedSeries = expected.objects("series").filter { it.string("id") != "compare-placeholder" }

            val chart = energy.fixture.hass.energyDevicesDetailGraph(recorded.data, maxDevices = null)

            // Upstream suffixes the untracked series' ids with the time to keep them last
            assertEquals(expectedSeries.map { it.string("id")!!.replace(Regex("(untracked(-negative)?)-\\d+$"), "$1") }, chart.series.map { it.id })
            assertEquals(expectedSeries.map { it.string("name") }, chart.series.map { it.name })
            expectedSeries.zip(chart.series).forEach { (series, actual) ->
                val points = (series["data"] as JsonArray).map { it as JsonArray }.filter { it.size == 3 }
                assertEquals(points.map { it[0].jsonPrimitive.long }, actual.points.map { it.x }, "${actual.id} x")
                assertEquals(points.map { it[2].jsonPrimitive.long }, actual.points.map { it.start }, "${actual.id} start")
                points.zip(actual.points).forEach { (point, bar) -> assertEquals(point[1].jsonPrimitive.double, bar.y, TOLERANCE) }
            }
            assertEquals(expected.number("yAxisFractionDigits")?.toInt(), chart.yFractionDigits)
        }
    }

    @TestFactory
    fun `Given a period's data when computing the devices graph then it has the frontend's bars and donut`(): List<DynamicTest> = energy.periods.flatMap { recorded ->
        listOf("energy-devices-graph" to DevicesChartType.BAR, "energy-devices-graph-pie" to DevicesChartType.PIE).map { (name, type) ->
            DynamicTest.dynamicTest("${recorded.name} $type") {
                val expected = recorded.card(name)
                val series = expected.objects("series")

                val model = energy.fixture.hass.energyDevicesGraph(recorded.data, type)

                val main = series.first().objects("data")
                assertEquals(main.map { it.string("id") }, model.slices.map { it.id })
                main.zip(model.slices).forEach { (slice, actual) -> assertEquals(slice.number("value")!!, actual.value, TOLERANCE) }
                assertEquals(expected.objects("legend").map { it.string("name") }, model.slices.map { it.name })
                assertEquals(expected.objects("legend").map { it.string("value") }, model.slices.map { it.valueText })
                val previous = series.firstOrNull { it.string("name") == "Previous energy usage" }?.objects("data")
                assertEquals(previous?.map { it.number("value") }, model.slices.map { it.compareValue }.takeIf { model.compare })
                val total = series.firstOrNull { it.string("name") == "Total" }?.objects("data")?.single()?.number("value")
                if (total != null) assertEquals(total, model.total!!, TOLERANCE) else assertEquals(null, model.total)
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
