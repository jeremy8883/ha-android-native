package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EntityNameTest {
    @ParameterizedTest(name = "\"{0}\" minus \"{1}\" -> \"{2}\"")
    @CsvSource(
        "Kitchen Lights, kitchen, Lights",
        "kitchen lights, Kitchen, Lights",
        "Kitchen: ceiling light, kitchen, Ceiling light",
        // Upstream quirk: " " is checked before " - ", so the dash stays
        "Kitchen - motion, kitchen, - motion",
        "Kitchen iPhone charger, kitchen, iPhone charger",
        "Living Room TV, living room, TV",
        "Kitchen, kitchen, ",
        "Kitchenette light, kitchen, ",
        "Bedroom lamp, kitchen, ",
    )
    fun `Given entity name and prefix when stripping then matches upstream`(name: String, prefix: String, expected: String?) {
        assertEquals(expected, stripPrefixFromEntityName(name, prefix))
    }

    @Test
    fun `Given state without friendly name when computing name then object id is used`() {
        val all = states("""{"light.living_room_lamp": {"s": "on", "a": {}}, "light.named": {"s": "on", "a": {"friendly_name": "Named"}}}""")
        assertEquals("living room lamp", all.getValue("light.living_room_lamp").stateName())
        assertEquals("Named", all.getValue("light.named").stateName())
    }
}
