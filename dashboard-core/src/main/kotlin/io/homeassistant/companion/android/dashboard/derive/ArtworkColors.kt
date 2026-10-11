package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.moreinfo.luminosity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// The colours the media control card takes from its artwork: `extractColors` (frontend@20260624.6
// src/common/image/extract_color.ts), which runs node-vibrant 4.0.4's pipeline (its default filter and the MMCQ
// quantizer of @vibrant/quantizer-mmcq) with the frontend's own generator.

/** The artwork's [background] (its most common colour) and a [foreground] readable on it, as ARGB. */
data class ArtworkColors(val foreground: Int, val background: Int)

/**
 * The colours of an image from its [pixels] (ARGB, already scaled down by the caller as node-vibrant does, to a
 * fifth of each side), or `null` when no pixel counts (all transparent or white), where upstream fails and keeps
 * the theme's colours.
 */
fun artworkColors(pixels: IntArray): ArtworkColors? {
    val histogram = Histogram(pixels.filter(::keptByDefaultFilter))
    if (histogram.total == 0) return null
    return generate(quantize(histogram, COLOR_COUNT))
}

/** node-vibrant's `default` filter: opaque enough and not near white. */
private fun keptByDefaultFilter(argb: Int): Boolean {
    val alpha = argb ushr ALPHA_SHIFT and CHANNEL
    val (r, g, b) = channels(argb)
    return alpha >= MIN_ALPHA && !(r > NEAR_WHITE && g > NEAR_WHITE && b > NEAR_WHITE)
}

private fun channels(argb: Int) = Triple(
    argb shr RED_SHIFT and CHANNEL,
    argb shr GREEN_SHIFT and CHANNEL,
    argb and CHANNEL,
)

/** A quantized colour and how many pixels it stands for. */
private data class Swatch(val r: Int, val g: Int, val b: Int, val population: Int) {
    val argb: Int get() = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b

    /** `Swatch.getYiq`. */
    val yiq: Double get() = (r * YIQ_RED + g * YIQ_GREEN + b * YIQ_BLUE) / YIQ_DIVISOR.toDouble()
}

/** Port of the frontend's `customGenerator`: the most common colour, and the first next one with enough contrast. */
private fun generate(swatches: List<Swatch>): ArtworkColors {
    val colors = swatches.sortedByDescending { it.population }
    val background = colors.first()
    val approved = { swatch: Swatch -> contrastRatio(background, swatch) > CONTRAST_RATIO }
    // The first next colour with enough contrast, else one similar to it that has some
    val foreground = (1 until colors.size).firstNotNullOfOrNull { i ->
        colors[i].takeIf(approved) ?: colors.drop(i + 1).firstOrNull { other ->
            val diff = abs(colors[i].r - other.r) + abs(colors[i].g - other.g) + abs(colors[i].b - other.b)
            diff <= COLOR_SIMILARITY_THRESHOLD && approved(other)
        }
    }
    val foregroundArgb = foreground?.argb ?: if (background.yiq < YIQ_LIGHT) WHITE else BLACK
    return ArtworkColors(foregroundArgb, background.argb)
}

/** Port of `getRGBContrastRatio`: the WCAG contrast, rounded to two decimals. */
private fun contrastRatio(a: Swatch, b: Swatch): Double {
    val la = luminosity(doubleArrayOf(a.r.toDouble(), a.g.toDouble(), a.b.toDouble()))
    val lb = luminosity(doubleArrayOf(b.r.toDouble(), b.g.toDouble(), b.b.toDouble()))
    val ratio = (max(la, lb) + WCAG_OFFSET) / (min(la, lb) + WCAG_OFFSET)
    return ((ratio + Math.ulp(1.0)) * HUNDRED).roundToLong() / HUNDRED
}

/** `Histogram` with 5 significant bits per channel. */
private class Histogram(pixels: List<Int>) {
    val hist = IntArray(1 shl (3 * SIGBITS))
    var rmin = Int.MAX_VALUE
    var rmax = 0
    var gmin = Int.MAX_VALUE
    var gmax = 0
    var bmin = Int.MAX_VALUE
    var bmax = 0
    val total = pixels.size

    init {
        pixels.forEach { argb ->
            val (r8, g8, b8) = channels(argb)
            val r = r8 shr RSHIFT
            val g = g8 shr RSHIFT
            val b = b8 shr RSHIFT
            hist[index(r, g, b)]++
            rmin = min(rmin, r)
            rmax = max(rmax, r)
            gmin = min(gmin, g)
            gmax = max(gmax, g)
            bmin = min(bmin, b)
            bmax = max(bmax, b)
        }
    }
}

private fun index(r: Int, g: Int, b: Int) = (r shl (2 * SIGBITS)) + (g shl SIGBITS) + b

/** The colour channel a box is cut along. */
private enum class Axis { R, G, B }

/** Port of `VBox`: a box of the 5-bit colour cube over [histogram], [r], [g] and [b] wide. */
private class VBox(val r: IntRange, val g: IntRange, val b: IntRange, val histogram: Histogram) {
    val volume: Int by lazy { (r.last - r.first + 1) * (g.last - g.first + 1) * (b.last - b.first + 1) }

    val count: Int by lazy {
        var c = 0
        forEachCell { _, _, _, h -> c += h }
        c
    }

    inline fun forEachCell(action: (Int, Int, Int, Int) -> Unit) {
        for (ri in r) {
            for (gi in g) {
                for (bi in b) {
                    val h = histogram.hist[index(ri, gi, bi)]
                    if (h != 0) action(ri, gi, bi, h)
                }
            }
        }
    }

    fun with(axis: Axis, range: IntRange) = when (axis) {
        Axis.R -> VBox(range, g, b, histogram)
        Axis.G -> VBox(r, range, b, histogram)
        Axis.B -> VBox(r, g, range, histogram)
    }

    fun range(axis: Axis) = when (axis) {
        Axis.R -> r
        Axis.G -> g
        Axis.B -> b
    }

    /** `avg`: the population-weighted mean colour, truncated; the box's centre when it is empty. */
    fun avg(): Triple<Int, Int, Int> {
        val mult = 1 shl RSHIFT
        var total = 0
        var rsum = 0.0
        var gsum = 0.0
        var bsum = 0.0
        forEachCell { ri, gi, bi, h ->
            total += h
            rsum += h * (ri + HALF) * mult
            gsum += h * (gi + HALF) * mult
            bsum += h * (bi + HALF) * mult
        }
        return if (total > 0) {
            Triple((rsum / total).toInt(), (gsum / total).toInt(), (bsum / total).toInt())
        } else {
            Triple(
                mult * (r.first + r.last + 1) / 2,
                mult * (g.first + g.last + 1) / 2,
                mult * (b.first + b.last + 1) / 2,
            )
        }
    }

    /** Port of `split`: cut along the longest side at the population's median; itself when one pixel. */
    fun split(): List<VBox> = when (count) {
        0 -> emptyList()
        1 -> listOf(this)
        else -> {
            val widths = listOf(Axis.R, Axis.G, Axis.B).map { it to range(it).last - range(it).first + 1 }
            // The first of the widest, in r, g, b order
            val axis = widths.first { it.second == widths.maxOf { (_, w) -> w } }.first
            cut(axis, cumulativeCounts(axis))
        }
    }

    /** `accSum`: the pixels up to each slice along [axis]. */
    private fun cumulativeCounts(axis: Axis): IntArray {
        val range = range(axis)
        val sums = IntArray(range.last + 1)
        forEachCell { ri, gi, bi, h ->
            sums[
                when (axis) {
                    Axis.R -> ri
                    Axis.G -> gi
                    Axis.B -> bi
                },
            ] += h
        }
        var total = 0
        for (i in range) {
            total += sums[i]
            sums[i] = total
        }
        return sums
    }

    /** `doCut`. */
    private fun cut(axis: Axis, accSum: IntArray): List<VBox> {
        val total = count
        var splitPoint = -1
        val reverseSum = IntArray(accSum.size)
        for (i in accSum.indices) {
            val d = accSum[i]
            if (d == 0) continue
            if (splitPoint < 0 && d > total / 2.0) splitPoint = i
            reverseSum[i] = total - d
        }
        val d1 = range(axis).first
        val originalD2 = range(axis).last
        val left = splitPoint - d1
        val right = originalD2 - splitPoint
        var d2 = if (left <= right) {
            max(0, min(originalD2 - 1, (splitPoint + right / 2.0).toInt()))
        } else {
            min(originalD2, max(d1, (splitPoint - 1 - left / 2.0).toInt()))
        }
        while (d2 < accSum.size && accSum[d2] == 0) d2++
        var c2 = reverseSum.getOrElse(d2) { 0 }
        while (c2 == 0 && accSum.getOrElse(d2 - 1) { 0 } != 0) c2 = reverseSum.getOrElse(--d2) { 0 }
        return listOf(with(axis, d1..d2), with(axis, d2 + 1..originalD2))
    }
}

/** Port of `PQueue`: sorted (stably) by [comparator] before each pop, which takes the last. */
private class PQueue(var comparator: Comparator<VBox>, val contents: MutableList<VBox> = mutableListOf()) {
    private var sorted = false

    fun push(box: VBox) {
        contents.add(box)
        sorted = false
    }

    fun pop(): VBox? {
        if (!sorted) {
            contents.sortWith(comparator)
            sorted = true
        }
        return contents.removeLastOrNull()
    }

    val size get() = contents.size
}

/** Port of `MMCQ`. */
private fun quantize(histogram: Histogram, colorCount: Int): List<Swatch> {
    val box = with(histogram) { VBox(rmin..rmax, gmin..gmax, bmin..bmax, this) }
    val pq = PQueue(compareBy { it.count })
    pq.push(box)
    splitBoxes(pq, FRACT_BY_POPULATIONS * colorCount)
    val pq2 = PQueue(compareBy<VBox> { it.count.toLong() * it.volume }, pq.contents)
    splitBoxes(pq2, (colorCount - pq2.size).toDouble())
    val swatches = mutableListOf<Swatch>()
    while (pq2.size > 0) {
        val v = pq2.pop() ?: break
        val (r, g, b) = v.avg()
        swatches.add(Swatch(r, g, b, v.count))
    }
    return swatches
}

/** `_splitBoxes`: until [target] boxes, or no box can be split further. */
private fun splitBoxes(pq: PQueue, target: Double) {
    var converged = false
    while (!converged && pq.size < target) {
        val lastSize = pq.size
        val parts = pq.pop()?.takeIf { it.count > 0 }?.split().orEmpty()
        parts.getOrNull(0)?.let(pq::push)
        parts.getOrNull(1)?.takeIf { it.count > 0 }?.let(pq::push)
        // Nothing split (an empty box, or one pixel): converged
        converged = parts.isEmpty() || pq.size == lastSize
    }
}

private const val COLOR_COUNT = 16
private const val FRACT_BY_POPULATIONS = 0.75
private const val SIGBITS = 5
private const val RSHIFT = 8 - SIGBITS
private const val HALF = 0.5
private const val CONTRAST_RATIO = 4.5
private const val COLOR_SIMILARITY_THRESHOLD = 150
private const val WCAG_OFFSET = 0.05
private const val HUNDRED = 100.0
private const val YIQ_RED = 299
private const val YIQ_GREEN = 587
private const val YIQ_BLUE = 114
private const val YIQ_DIVISOR = 1000
private const val YIQ_LIGHT = 200
private const val MIN_ALPHA = 125
private const val NEAR_WHITE = 250
private const val CHANNEL = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = 0xFF shl ALPHA_SHIFT
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
