package io.homeassistant.companion.android.dashboard.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChartZoomTest {

    @Test
    fun `Given a whole chart when spreading two fingers at its middle then it zooms in by a tenth around them`() {
        val zoom = ChartZoom().pinched(focus = 0.5, spreading = true)

        assertEquals(50 - 50 / 1.1, zoom.start, DELTA)
        assertEquals(50 + 50 / 1.1, zoom.end, DELTA)
        assertTrue(zoom.isZoomed)
    }

    @Test
    fun `Given a zoomed chart when closing fingers near an edge then each end stays within the axis`() {
        val zoom = ChartZoom(0.0, 50.0).pinched(focus = 0.0, spreading = false)

        assertEquals(0.0, zoom.start, DELTA)
        assertEquals(55.0, zoom.end, DELTA)
        assertEquals(ChartZoom(), ChartZoom(2.0, 98.0).pinched(focus = 0.5, spreading = false))
    }

    @Test
    fun `Given a zoomed chart when dragging right then it shows earlier times, its span kept, never past the start`() {
        val zoom = ChartZoom(40.0, 60.0)

        assertEquals(ChartZoom(30.0, 50.0), zoom.panned(pixels = 150.0, plotWidth = 300.0))
        assertEquals(ChartZoom(0.0, 20.0), zoom.panned(pixels = 3000.0, plotWidth = 300.0))
        assertEquals(ChartZoom(80.0, 100.0), zoom.panned(pixels = -3000.0, plotWidth = 300.0))
    }

    @Test
    fun `Given a chart when double tapping then it zooms in on the 30 percent around the tap, and out on the next`() {
        val zoomed = ChartZoom().doubleTapped(x = 0.5)

        assertEquals(ChartZoom(35.0, 65.0), zoomed)
        assertEquals(ChartZoom(0.0, 15.0), ChartZoom().doubleTapped(x = 0.0))
        assertFalse(zoomed.doubleTapped(x = 0.2).isZoomed)
    }

    @Test
    fun `Given a zoom when asking what shows then it is that share of the axis and its ratio`() {
        val zoom = ChartZoom(40.0, 60.0)

        assertEquals(1400.0 to 1600.0, zoom.window(1000.0, 2000.0))
        assertEquals(0.2, zoom.ratio, DELTA)
    }

    @Test
    fun `Given a zoomed line when fitting its value axis then the points shown and one beyond each side count`() {
        val points = listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0)

        assertEquals(listOf(10.0, 20.0, 30.0, 40.0), points.inZoomWindow(15.0, 35.0) { it })
        assertEquals(listOf(0.0, 10.0), points.inZoomWindow(-10.0, 5.0) { it })
    }

    private companion object {
        const val DELTA = 1e-9
    }
}
