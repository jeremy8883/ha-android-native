package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import io.homeassistant.companion.android.dashboard.energy.StatisticValue
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HistoryPanelTest {

    private val hass = GoldenFixture("test-instance").hass
    private val sensor = "sensor.living_room_temperature"

    @Test
    fun `Given hourly statistics when converting them then each period's mean (else state) is a line point at its end`() {
        val statistics = mapOf(
            sensor to listOf(
                StatisticValue(start = 0, end = 3_600_000, mean = 21.0),
                StatisticValue(start = 3_600_000, end = 7_200_000, state = 21.5),
            ),
        )

        val entity = hass.convertStatisticsToHistory(statistics, listOf(sensor)).line.single().data.single()

        assertEquals(emptyList<LineChartState>(), entity.states)
        assertEquals(listOf("21" to 3_600_000L, "21.5" to 7_200_000L), entity.statistics!!.map { it.state to it.lastChanged })
    }

    @Test
    fun `Given statistics overlapping the states when merging them then only those before the first state are kept`() {
        val states = mapOf(sensor to listOf(HistoryState("22", null, null, 5_000.0)))
        val statistics = mapOf(
            sensor to listOf(
                StatisticValue(start = 0, end = 3_600_000, mean = 21.0),
                StatisticValue(start = 3_600_000, end = 7_200_000, mean = 21.5),
            ),
        )
        val history = hass.computeHistory(states, listOf(sensor), splitDeviceClasses = true)
        val lts = hass.convertStatisticsToHistory(statistics, listOf(sensor), splitDeviceClasses = true)

        val merged = mergeHistoryResults(history, lts).line.single().data.single()

        assertEquals(listOf("22"), merged.states.map { it.state })
        assertEquals(listOf(3_600_000L), merged.statistics!!.map { it.lastChanged })
    }

    @Test
    fun `Given statistics after every state when merging them then the states are kept alone`() {
        val states = mapOf(sensor to listOf(HistoryState("22", null, null, 1.0)))
        val statistics = mapOf(sensor to listOf(StatisticValue(start = 0, end = 3_600_000, mean = 21.0)))
        val history = hass.computeHistory(states, listOf(sensor), splitDeviceClasses = true)
        val lts = hass.convertStatisticsToHistory(statistics, listOf(sensor), splitDeviceClasses = true)

        assertNull(mergeHistoryResults(history, lts).line.single().data.single().statistics)
    }

    @Test
    fun `Given a history when downloading it then the CSV has a row per state, climate columns and quoted states`() {
        val climate = LineChartEntity(
            "climate",
            "Hvac",
            "climate.hvac",
            listOf(LineChartState("cool", 0, JsonObject(mapOf("temperature" to JsonPrimitive(21), "hvac_action" to JsonPrimitive("cooling"))))),
        )
        val history = HistoryResult(
            line = listOf(LineChartUnit("°C", "temperature", "climate.hvac", listOf(climate))),
            timeline = listOf(TimelineEntity("Mode", "input_select.mode", listOf(TimelineState("Away, long", "away, long", 1000)))),
        )

        assertEquals(
            "entity_id,state,last_changed,current_temperature,hvac_action,target_temp_high,target_temp_low,temperature\n" +
                "climate.hvac,cool,1970-01-01T00:00:00.000Z,,cooling,,,21\n" +
                "input_select.mode,\"away, long\",1970-01-01T00:00:01.000Z\n",
            historyCsv(history),
        )
    }

    @Test
    fun `Given spans of time when labelling the time axis then it reads as the frontend's`() {
        val formats = JdkDisplayFormats(Locale.US, ZoneOffset.UTC)
        val midnight = Instant.parse("2026-10-11T00:00:00Z").toEpochMilli()
        val noon = Instant.parse("2026-10-11T12:00:00Z").toEpochMilli()
        val hours = 3 * 3_600_000L
        val days = 24 * 3_600_000L

        assertEquals(TimeAxisLabel("Oct 11", bold = true), formats.historyTimeLabel(midnight, hours))
        assertEquals(TimeAxisLabel("12:00 PM"), formats.historyTimeLabel(noon, hours))
        assertEquals(TimeAxisLabel("Sun"), formats.historyTimeLabel(noon, 3 * days))
        assertEquals(TimeAxisLabel("Oct 11"), formats.historyTimeLabel(noon, 10 * days))
        assertEquals(TimeAxisLabel("October"), formats.historyTimeLabel(noon, 100 * days))
    }
}
