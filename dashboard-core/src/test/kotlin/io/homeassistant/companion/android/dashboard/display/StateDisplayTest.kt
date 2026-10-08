package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.states
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Display paths the golden fixtures don't reach (EntityDisplayGoldenTest covers the rest), with expectations
 * taken from the frontend's Intl output in Chromium for en-US and UTC.
 */
class StateDisplayTest {

    @ParameterizedTest
    @CsvSource(
        "26.5, h, 26h 30m",
        "1.25, d, 1d 6h",
        "3, d, 3d",
        "12.5, min, 12m 30s",
    )
    fun `Given a duration sensor when formatting its state then it is shown as a narrow duration`(
        value: String,
        unit: String,
        expected: String,
    ) {
        val hass = hass(
            states("""{"sensor.run": {"s": "$value", "a": {"device_class": "duration", "unit_of_measurement": "$unit"}}}"""),
        )
        assertEquals(expected, hass.formatEntityState(hass.states.getValue("sensor.run")))
    }

    @Test
    fun `Given a monetary sensor when formatting its state then it is shown as currency with 2 decimals`() {
        val hass = hass(
            states("""{"sensor.cost": {"s": "12.5", "a": {"device_class": "monetary", "unit_of_measurement": "EUR"}}}"""),
        )
        assertEquals("€12.50", hass.formatEntityState(hass.states.getValue("sensor.cost")))
    }

    @Test
    fun `Given a value with more digits than the default when formatting then it rounds half away from zero on the exact value`() {
        // 1.005 is 1.00499999999999989... in binary, which Intl rounds down
        val hass = hass(states("""{"sensor.x": {"s": "1.005", "a": {"unit_of_measurement": "V"}}, "number.y": {"s": "2.5", "a": {"step": 1}}}"""))
        assertEquals("1.005 V", hass.formatEntityState(hass.states.getValue("sensor.x")))
        assertEquals("2.5", hass.formatEntityState(hass.states.getValue("number.y")))
    }

    @ParameterizedTest
    @CsvSource(
        "2026-10-08T12:00:20Z, In 20 seconds",
        "2026-10-08T11:58:00Z, 2 minutes ago",
        "2026-10-08T12:30:00Z, In 30 minutes",
        "2026-10-07T12:00:00Z, Yesterday",
        "2026-10-11T12:00:00Z, In 3 days",
        "2026-10-16T12:00:00Z, Next week",
        "2026-11-20T12:00:00Z, Next month",
        "2025-09-03T12:00:00Z, Last year",
    )
    fun `Given a timestamp sensor when showing its state content then it is a capitalised relative time`(
        timestamp: String,
        expected: String,
    ) {
        val hass = hass(states("""{"sensor.t": {"s": "$timestamp", "a": {"device_class": "timestamp"}}}"""))
        assertEquals(expected, hass.stateDisplay(hass.states.getValue("sensor.t"), content = null, now = NOW))
    }

    @Test
    fun `Given an active timer when showing its default content then it shows the time remaining`() {
        val hass = hass(
            states(
                """{"timer.tea": {"s": "active", "a": {"remaining": "0:05:00", "finishes_at": "2026-10-08T12:01:30+00:00"}}}""",
            ),
        )
        assertEquals("1:30", hass.stateDisplay(hass.states.getValue("timer.tea"), content = null, now = NOW))
    }

    @Test
    fun `Given an update installing with progress when showing its default content then it shows the progress`() {
        val base = hass(
            states(
                """{"update.fw": {"s": "on", "a": {"in_progress": true, "update_percentage": 42, "supported_features": 5}}}""",
            ),
        )
        val hass = base.copy(localize = Localize { if (it == "ui.card.update.installing_with_progress") "Installing ({progress}%)" else "" })
        assertEquals("Installing (42%)", hass.stateDisplay(hass.states.getValue("update.fw"), content = null, now = NOW))
    }

    @Test
    fun `Given explicit state content when showing it then the parts are joined and empty ones skipped`() {
        val hass = hass(
            states("""{"light.desk": {"s": "on", "a": {"brightness": 0, "color_temp_kelvin": 3000}}}"""),
        )
        val content = kotlinx.serialization.json.Json.parseToJsonElement("""["brightness", "color_temp_kelvin", "missing"]""")
        assertEquals("3,000 K", hass.stateDisplay(hass.states.getValue("light.desk"), content, now = NOW))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-08T12:00:00Z")
    }
}
