package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of what the energy dashboard fetches for a period against the requests the real frontend
 * (20260624.6) made for the same periods, captured by tools/golden/capture.mjs on the local test instance.
 */
class EnergyFetchGoldenTest {

    private val energy = EnergyFixture()

    @TestFactory
    fun `Given a period when planning its energy data then the requests are the frontend's`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val plan = recorded.plan

            assertEquals(
                recorded.requests.map { it.obj("request")!!.withoutId() },
                listOf(ENERGY_INFO_COMMAND, energy.prefs.metadataCommand(recorded.info)!!).plus(plan.commands)
                    .map { it.message() },
            )
        }
    }

    @TestFactory
    fun `Given the frontend's responses when assembling the energy data then it has the frontend's`(): List<DynamicTest> = energy.periods.map { recorded ->
        DynamicTest.dynamicTest(recorded.name) {
            val data = recorded.data

            val expected = recorded.json.obj("data")!!
            assertEquals(expected.string("waterUnit"), data.waterUnit)
            assertEquals(expected.string("gasUnit"), data.gasUnit)
            assertEquals(expected.string("startCompare")?.let(Instant::parse), data.comparePeriod?.startInstant(ZoneOffset.UTC))
            assertEquals(expected.string("endCompare")?.let(Instant::parse), data.comparePeriod?.endInstant(ZoneOffset.UTC))
            assertEquals(expected["statIds"]!!.jsonArray.map { it.toString().trim('"') }, data.stats.keys.sorted())
        }
    }
}
