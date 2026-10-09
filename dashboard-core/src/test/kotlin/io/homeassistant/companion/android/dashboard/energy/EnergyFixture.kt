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

/**
 * The energy data the frontend loaded for each period on the test instance (energy/data.json, captured by
 * tools/golden/capture.mjs), with what its cards showed.
 */
internal class EnergyFixture {
    val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
    val prefs = EnergyPreferences.fromJson(checkNotNull(fixture.strategyData.energyPrefs))
    private val root = fixture.json("energy/data.json")

    /** When the frontend loaded the data, which decides whether "today" includes now. */
    val capturedAt: Instant = Instant.parse(checkNotNull(root.string("capturedAt")))

    val periods: List<RecordedPeriod> = root.objects("periods").map(::RecordedPeriod)

    inner class RecordedPeriod(val json: JsonObject) {
        val name: String = json.string("name").orEmpty()
        val requests: List<JsonObject> = json.objects("requests")
        val info = EnergyInfo.fromJson(result("energy/info") as JsonObject)
        val metadata = parseStatisticsMetadata(result("recorder/get_statistics_metadata") as JsonArray)

        val request: EnergyRequest
            get() {
                val start = Instant.parse(json.string("start")!!).atZone(ZoneOffset.UTC).toLocalDate()
                val end = Instant.parse(json.string("end")!!).atZone(ZoneOffset.UTC).toLocalDate()
                val compare = CompareMode.entries.firstOrNull { it.value == json.string("compare") }
                return EnergyRequest(EnergyPeriod(start, end), compare, ZoneOffset.UTC)
            }

        val plan: EnergyFetchPlan
            get() = planEnergyFetch(prefs, info, metadata, request, fixture.hass.energyEnvironment(prefs))

        /** The data assembled from the frontend's responses. */
        val data: EnergyData
            get() {
                val plan = plan
                fun result(command: WsCommand?) = command?.let { c ->
                    requests.first { it.obj("request")!!.withoutId() == c.message() }["result"] as JsonObject
                }
                return plan.assemble(
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
                        // Fetched by the solar graph itself, which the capture records with it
                        solarForecast = json.obj("cards")?.obj("energy-solar-graph")?.obj("forecasts")
                            .takeIf { prefs.hasSolarForecast },
                    ),
                )
            }

        /** What the frontend's card of [type] showed. */
        fun card(type: String): JsonObject = checkNotNull(json.obj("cards")?.obj(type)) { "No $type in $name" }

        fun result(type: String) = requests.first { it.obj("request")?.string("type") == type }["result"]
    }
}

internal fun JsonObject.withoutId() = JsonObject(filterKeys { it != "id" })

internal fun WsCommand.message() = JsonObject(mapOf("type" to JsonPrimitive(type)) + params)
