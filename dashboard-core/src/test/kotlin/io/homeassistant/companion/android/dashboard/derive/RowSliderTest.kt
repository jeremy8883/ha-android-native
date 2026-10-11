package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.feature.ValueService
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RowSliderTest {
    private fun slider(min: Double, max: Double, step: Double) = RowSlider(
        value = null,
        min = min,
        max = max,
        step = step,
        state = "",
        enabled = true,
        service = ValueService("input_number", "set_value", JsonObject(emptyMap()), "value"),
    )

    @Test
    fun `Given a dragged value when labelling the slider then it shows the value on its step`() {
        assertEquals("0.3", slider(0.0, 1.0, 0.1).tooltip(0.2999))
        assertEquals("42", slider(0.0, 100.0, 1.0).tooltip(41.6))
        assertEquals("15", slider(5.0, 25.0, 5.0).tooltip(14.0))
        assertEquals("25", slider(5.0, 25.0, 5.0).tooltip(25.0))
    }
}
