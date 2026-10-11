package io.homeassistant.companion.android.dashboard.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartLegendStateTest {

    private val all = listOf("a", "b", "c")

    @Test
    fun `Given a shown item when tapping it then it is hidden, and shown again on a second tap`() {
        assertEquals(setOf("a"), legendToggled(emptySet(), "a"))
        assertEquals(emptySet<String>(), legendToggled(setOf("a"), "a"))
    }

    @Test
    fun `Given all shown when long-pressing an item then only it is shown`() {
        assertEquals(setOf("a", "c"), legendSoloed(all, emptySet(), "b"))
    }

    @Test
    fun `Given a hidden item when long-pressing it then it alone is shown`() {
        assertEquals(setOf("a", "c"), legendSoloed(all, setOf("b", "c"), "b"))
    }

    @Test
    fun `Given the only shown item when long-pressing it then every item is shown again`() {
        assertEquals(emptySet<String>(), legendSoloed(all, setOf("a", "c"), "b"))
    }

    @Test
    fun `Given a phone or a short screen when sizing the legend then it shows 6 items, else 10`() {
        assertEquals(6, legendOverflowLimit(411, 891))
        assertEquals(6, legendOverflowLimit(891, 411))
        assertEquals(10, legendOverflowLimit(800, 1280))
    }
}
