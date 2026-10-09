package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of what the energy dashboard fetches for a period against the requests the real frontend
 * (20260624.6) made for the same periods, captured by tools/golden/capture.mjs on the local test instance.
 */
class EnergyFetchGoldenTest {

    private val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
    private val prefs = EnergyPreferences.fromJson(checkNotNull(fixture.strategyData.energyPrefs))

    @TestFactory
    fun `Given a period when planning its energy data then the requests are the frontend's`(): List<DynamicTest> = periods().map { recorded ->
        DynamicTest.dynamicTest(recorded.string("name").orEmpty()) {
            val requests = recorded.objects("requests")
            val info = EnergyInfo.fromJson(requests.result("energy/info") as JsonObject)
            val metadata = parseStatisticsMetadata(requests.result("recorder/get_statistics_metadata") as JsonArray)

            val plan = planEnergyFetch(prefs, info, metadata, recorded.request(), fixture.hass.energyEnvironment(prefs))

            assertEquals(
                requests.map { it.obj("request")!!.withoutId() },
                listOf(ENERGY_INFO_COMMAND, prefs.metadataCommand(info)!!).plus(plan.commands).map { it.message() },
            )
        }
    }

    @TestFactory
    fun `Given the frontend's responses when assembling the energy data then it has the frontend's`(): List<DynamicTest> = periods().map { recorded ->
        DynamicTest.dynamicTest(recorded.string("name").orEmpty()) {
            val requests = recorded.objects("requests")
            val info = EnergyInfo.fromJson(requests.result("energy/info") as JsonObject)
            val metadata = parseStatisticsMetadata(requests.result("recorder/get_statistics_metadata") as JsonArray)
            val plan = planEnergyFetch(prefs, info, metadata, recorded.request(), fixture.hass.energyEnvironment(prefs))
            fun result(command: WsCommand?) = command?.let { c ->
                requests.first { it.obj("request")!!.withoutId() == c.message() }["result"] as JsonObject
            }

            val data = plan.assemble(
                prefs,
                info,
                metadata,
                EnergyFetchResults(
                    energy = result(plan.energy),
                    power = result(plan.power),
                    powerHour = result(plan.powerHour),
                    water = result(plan.water),
                    energyCompare = result(plan.energyCompare),
                    waterCompare = result(plan.waterCompare),
                ),
            )

            val expected = recorded.obj("data")!!
            assertEquals(expected.string("waterUnit"), data.waterUnit)
            assertEquals(expected.string("gasUnit"), data.gasUnit)
            assertEquals(expected.string("startCompare")?.let(Instant::parse), data.comparePeriod?.startInstant(ZoneOffset.UTC))
            assertEquals(expected.string("endCompare")?.let(Instant::parse), data.comparePeriod?.endInstant(ZoneOffset.UTC))
            assertEquals(expected["statIds"]!!.jsonArray.map { it.toString().trim('"') }, data.stats.keys.sorted())
        }
    }

    private fun periods(): List<JsonObject> = fixture.json("energy/data.json").objects("periods")

    private fun JsonObject.request(): EnergyRequest {
        val start = Instant.parse(string("start")!!).atZone(ZoneOffset.UTC).toLocalDate()
        val end = Instant.parse(string("end")!!).atZone(ZoneOffset.UTC).toLocalDate()
        val compare = CompareMode.entries.firstOrNull { it.value == string("compare") }
        return EnergyRequest(EnergyPeriod(start, end), compare, ZoneOffset.UTC)
    }

    private fun List<JsonObject>.result(type: String) = first { it.obj("request")?.string("type") == type }["result"]

    private fun JsonObject.withoutId() = JsonObject(filterKeys { it != "id" })

    private fun WsCommand.message() = JsonObject(mapOf("type" to JsonPrimitive(type)) + params)
}
