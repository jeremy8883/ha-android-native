package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The forecast features' drawing, with expectations computed from upstream's code. */
class ForecastFeaturesTest {
    private fun feature(kind: ForecastKind, resolution: String = "daily", entries: String) = ForecastFeature(
        kind = kind,
        resolution = resolution,
        entries = json("""{"e": $entries}""")["e"].let { array ->
            (array as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
        },
        daysToShow = 7,
        hoursToShow = 24,
        showLabels = true,
        color = null,
        currentTemperature = null,
        fahrenheit = false,
        imperial = false,
        noForecast = "",
        nowMillis = 0.0,
        amPm = true,
        zone = java.time.ZoneOffset.UTC,
    )

    @Test
    fun `Given the palette when building gradients then stops run from the high to the low temperature`() {
        val stops = absoluteGradient(-6.0, 42.0, 2)
        assertEquals(listOf(0.0 to "#c82c1d", 1.0 to "#249df2"), stops.map { it.offset to it.color })
        // Halfway between 10 (#43c9c0) and 11 (#59c9b3)
        assertEquals("#4ec9ba", absoluteGradient(10.5, 10.5, 2).first().color)
    }

    @Test
    fun `Given daily temperatures when drawing bars then each spans its low to high within the padding`() {
        val forecast = feature(
            ForecastKind.Temperature,
            entries = """[{"datetime": "2026-10-12T00:00:00Z", "temperature": 20, "templow": 10},
                {"datetime": "2026-10-13T00:00:00Z", "temperature": 15, "templow": 5}]""",
        )
        val bars = forecast.temperatureBars(forecast.dailyEntries(), 100.0, 42.0)
        // slot 50, bar 8 wide; drawable 34 from 4, temperatures 5..20
        assertEquals(listOf(21.0, 71.0), bars.map { it.x })
        assertEquals(4.0, bars[0].y, 1e-9)
        assertEquals(34.0 * 10 / 15, bars[0].height, 1e-9)
        assertEquals(3, bars[0].stops?.size)
    }

    @Test
    fun `Given dry and wet days when drawing precipitation then dry days are dots and bars scale to 10 mm`() {
        val forecast = feature(
            ForecastKind.Precipitation(probability = false),
            entries = """[{"datetime": "2026-10-12T00:00:00Z", "precipitation": 0},
                {"datetime": "2026-10-13T00:00:00Z", "precipitation": 5}]""",
        )
        val marks = forecast.precipitationMarks(100.0, 42.0)
        assertTrue(marks[0] is PrecipitationMark.Dot)
        val bar = marks[1] as PrecipitationMark.Bar
        assertEquals(17.0, bar.height, 1e-9)
        assertEquals(16.0, bar.width, 1e-9)
    }
}
