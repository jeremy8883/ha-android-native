package io.homeassistant.companion.android.dashboard.display

import kotlin.math.pow
import kotlin.math.roundToInt

// CIELAB lightness changes of the frontend's colours. Port of frontend@20260624.6 src/common/color/convert-color.ts
// and lab.ts (from chroma.js).

/**
 * [rgb] (0xRRGGBB) darkened by [amount] steps of lightness, or brightened when [brighten]. Port of `labDarken` and
 * `labBrighten` on `rgb2lab`/`lab2rgb`.
 */
fun shadeRgb(rgb: Int, amount: Double, brighten: Boolean): Int {
    val (l, a, b) = rgbToLab(rgb)
    val sign = if (brighten) 1 else -1
    return labToRgb(Triple(l + sign * LIGHTNESS_STEP * amount, a, b))
}

private fun rgbToLab(rgb: Int): Triple<Double, Double, Double> {
    val r = rgbToXyz((rgb shr RED_SHIFT) and CHANNEL)
    val g = rgbToXyz((rgb shr GREEN_SHIFT) and CHANNEL)
    val b = rgbToXyz(rgb and CHANNEL)
    val x = xyzToLab((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN)
    val y = xyzToLab((0.2126729 * r + 0.7151522 * g + 0.072175 * b) / YN)
    val z = xyzToLab((0.0193339 * r + 0.119192 * g + 0.9503041 * b) / ZN)
    val l = L_SCALE * y - L_OFFSET
    return Triple(if (l < 0) 0.0 else l, A_SCALE * (x - y), B_SCALE * (y - z))
}

private fun labToRgb(lab: Triple<Double, Double, Double>): Int {
    val (l, a, b) = lab
    val yLab = (l + L_OFFSET) / L_SCALE
    val x = XN * labToXyz(yLab + a / A_SCALE)
    val y = YN * labToXyz(yLab)
    val z = ZN * labToXyz(yLab - b / B_SCALE)
    val red = channel(xyzToRgb(3.2404542 * x - 1.5371385 * y - 0.4985314 * z))
    val green = channel(xyzToRgb(-0.969266 * x + 1.8760108 * y + 0.041556 * z))
    val blue = channel(xyzToRgb(0.0556434 * x - 0.2040259 * y + 1.0572252 * z))
    return (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

private fun channel(value: Double) = value.roundToInt().coerceIn(0, CHANNEL)

private fun rgbToXyz(channel: Int): Double {
    val c = channel / CHANNEL.toDouble()
    return if (c <= SRGB_LINEAR_LIMIT) c / SRGB_SLOPE else ((c + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
}

private fun xyzToLab(t: Double) = if (t > T3) t.pow(CUBE_ROOT) else t / T2 + T0

private fun xyzToRgb(r: Double) =
    CHANNEL * if (r <= XYZ_LINEAR_LIMIT) SRGB_SLOPE * r else (1 + SRGB_OFFSET) * r.pow(1 / SRGB_GAMMA) - SRGB_OFFSET

private fun labToXyz(t: Double) = if (t > T1) t * t * t else T2 * (t - T0)

private const val LIGHTNESS_STEP = 18
private const val CUBE_ROOT = 1.0 / 3
private const val XN = 0.95047
private const val YN = 1.0
private const val ZN = 1.08883
private const val T0 = 0.137931034
private const val T1 = 0.206896552
private const val T2 = 0.12841855
private const val T3 = 0.008856452
private const val L_SCALE = 116
private const val L_OFFSET = 16
private const val A_SCALE = 500
private const val B_SCALE = 200
private const val SRGB_LINEAR_LIMIT = 0.04045
private const val XYZ_LINEAR_LIMIT = 0.00304
private const val SRGB_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_GAMMA = 2.4
private const val CHANNEL = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
