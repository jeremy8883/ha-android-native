package io.homeassistant.companion.android.dashboard.history

// How the history charts zoom on a touch screen. Ports of the frontend's zoom (`_getDataZoomConfig`,
// `_handleClickZoom` and `_handleDataZoomEvent` in frontend@20260624.6 src/components/chart/ha-chart-base.ts), of
// ECharts 6.1.0's inside zoom it configures (`getRangeHandlers` in lib/component/dataZoom/InsideZoomView.js,
// `_pinchHandler` in lib/component/helper/RoamController.js, lib/component/helper/sliderMove.js) and of the
// frontend's `boundaryFilter` (src/resources/echarts/axis-proxy-patch.ts).

/**
 * The part of a chart's time axis shown, as percentages of it from [start] to [end] (0 to 100 when not zoomed).
 */
data class ChartZoom(val start: Double = 0.0, val end: Double = FULL) {
    /** Whether only part of the axis shows: its reset button shows, and a drag pans it. */
    val isZoomed: Boolean get() = start != 0.0 || end != FULL

    /** The share of the axis shown, which picks the labels' format as a span that long would. */
    val ratio: Double get() = (end - start) / FULL

    /** The times shown of an axis from [axisStart] to [axisEnd]. */
    fun window(axisStart: Double, axisEnd: Double): Pair<Double, Double> {
        val span = axisEnd - axisStart
        return axisStart + span * start / FULL to axisStart + span * end / FULL
    }

    /**
     * After a step of a pinch at [focus] (its share of the plot's width from the left): [spreading] fingers zoom in
     * by 10% around it, closing ones out, each end held within the axis.
     */
    fun pinched(focus: Double, spreading: Boolean): ChartZoom {
        val point = focus * (end - start) + start
        val scale = if (spreading) 1 / PINCH_STEP else PINCH_STEP
        return ChartZoom(
            ((start - point) * scale + point).coerceIn(0.0, FULL),
            ((end - point) * scale + point).coerceIn(0.0, FULL),
        )
    }

    /** After a drag of [pixels] (right positive) over a plot [plotWidth] wide: moved the other way, its span kept. */
    fun panned(pixels: Double, plotWidth: Double): ChartZoom {
        if (plotWidth <= 0) return this
        val span = end - start
        val delta = -span * pixels / plotWidth
        val from = (start + delta).coerceIn(0.0, FULL - span)
        return ChartZoom(from, from + span)
    }

    /**
     * After a double tap at [x] (its share of the whole chart's width, axis labels included, as upstream measures
     * it): zoomed out when zoomed, else in on the 30% around it.
     */
    fun doubleTapped(x: Double): ChartZoom = if (isZoomed) {
        ChartZoom()
    } else {
        val at = x * FULL
        ChartZoom((at - DOUBLE_TAP_HALF).coerceIn(0.0, FULL), (at + DOUBLE_TAP_HALF).coerceIn(0.0, FULL))
    }

    companion object {
        private const val FULL = 100.0
        private const val PINCH_STEP = 1.1
        private const val DOUBLE_TAP_HALF = 15.0
    }
}

/**
 * The values of a line series with points at [times] that its value axis fits when [from] to [to] shows: those
 * in it and the nearest just outside each side, so the line reaches the edges. Port of `boundaryFilter`.
 */
fun <T> List<T>.inZoomWindow(from: Double, to: Double, time: (T) -> Double): List<T> {
    val before = indexOfLast { time(it) < from }
    val after = indexOfFirst { time(it) > to }
    return filterIndexed { index, point -> index == before || index == after || time(point) in from..to }
}
