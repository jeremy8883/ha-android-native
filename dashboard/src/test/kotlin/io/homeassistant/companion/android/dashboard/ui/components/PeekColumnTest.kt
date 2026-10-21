package io.homeassistant.companion.android.dashboard.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeekColumnTest {

    private fun gap(heights: List<Int>, viewport: Int) = peekExtraGap(
        heights = heights,
        viewportHeight = viewport,
        padding = 0..0,
        rules = PeekRules(extraRange = -2..4, minPeekHeight = 16),
    )

    /** How much of the child crossing [viewport] shows with [extra] added between children. */
    private fun shown(heights: List<Int>, viewport: Int, extra: Int): Float {
        var y = 0
        heights.forEach { height ->
            if (y + height > viewport) return (viewport - y).toFloat() / height
            y += height + extra
        }
        return 1f
    }

    @Test
    fun `Given content that fits when spacing it then nothing changes`() {
        assertEquals(0, gap(List(5) { 40 }, viewport = 200))
    }

    @Test
    fun `Given a child already cut through its middle when spacing it then nothing changes`() {
        assertEquals(0, gap(List(10) { 40 }, viewport = 220))
    }

    @Test
    fun `Given the bottom edge between two children when spacing them then one is cut through its middle`() {
        val heights = List(10) { 40 }

        val extra = gap(heights, viewport = 200)

        assertTrue(extra in -2..4)
        assertTrue(shown(heights, viewport = 200, extra = extra) in 0.3f..0.7f)
    }

    @Test
    fun `Given a divider at the bottom edge when spacing them then a taller child is cut instead`() {
        val heights = List(6) { 40 } + listOf(1, 40, 40)

        val extra = gap(heights, viewport = 241)

        assertTrue(shown(heights, viewport = 241, extra = extra) in 0.3f..0.7f)
    }
}
