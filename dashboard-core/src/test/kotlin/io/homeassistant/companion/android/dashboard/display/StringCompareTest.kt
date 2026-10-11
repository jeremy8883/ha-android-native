package io.homeassistant.companion.android.dashboard.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringCompareTest {

    @Test
    fun `Given names when sorting them as the browser does then spaces, punctuation and numbers count`() {
        val sorted = listOf("Bedroom", "Bed Light_Bedroom", "bed light schedule", "Light 10", "Light 9", "Éclair", "eclair")
            .sortedWith(CaseInsensitiveOrder)

        assertEquals(listOf("bed light schedule", "Bed Light_Bedroom", "Bedroom", "eclair", "Éclair", "Light 9", "Light 10"), sorted)
    }

    @Test
    fun `Given names equal but for case when comparing them then case-insensitively they're equal and lower case goes first otherwise`() {
        assertEquals(0, caseInsensitiveStringCompare("Kitchen", "kitchen"))
        assertEquals(-1, stringCompare("kitchen", "Kitchen"))
    }
}
