package io.homeassistant.companion.android.dashboard.color

// Ports of the frontend's colour conversions (frontend@20260624.6 src/common/color/convert-color.ts). Channels are
// 0–255, hue in degrees, saturation 0–1.

/** Port of `rgb2hsv`: hue, saturation and value (0–255) of a colour. */
fun rgb2hsv(r: Double, g: Double, b: Double): DoubleArray {
    val v = maxOf(r, g, b)
    val c = v - minOf(r, g, b)
    val h = when {
        c == 0.0 -> 0.0
        v == r -> (g - b) / c
        v == g -> 2 + (b - r) / c
        else -> 4 + (r - g) / c
    }
    return doubleArrayOf(DEGREES_PER_SECTOR * (if (h < 0) h + SECTORS else h), if (v == 0.0) 0.0 else c / v, v)
}

/** Port of `hsv2rgb`. */
fun hsv2rgb(hsv: DoubleArray): DoubleArray {
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    fun f(n: Int): Double {
        val k = (n + h / DEGREES_PER_SECTOR) % SECTORS
        return v - v * s * maxOf(minOf(k, HSV_RAMP_END - k, 1.0), 0.0)
    }
    return doubleArrayOf(f(HSV_RED), f(HSV_GREEN), f(HSV_BLUE))
}

/** Port of `hs2rgb`: the colour of a hue and saturation at full value. */
fun hs2rgb(hue: Double, saturation: Double): DoubleArray = hsv2rgb(doubleArrayOf(hue, saturation, RGB_MAX))

/** Port of `rgb2hex`: `#rrggbb`. */
fun rgb2hex(rgb: DoubleArray): String = "#" + rgb.joinToString("") {
    Math.round(it.coerceIn(0.0, RGB_MAX)).toString(HEX_RADIX).padStart(2, '0')
}

// The `n` of each channel and the end of the ramp in upstream's `hsv2rgb` formula
private const val HSV_RED = 5
private const val HSV_GREEN = 3
private const val HSV_BLUE = 1
private const val HSV_RAMP_END = 4
internal const val RGB_MAX = 255.0
private const val HEX_RADIX = 16
private const val DEGREES_PER_SECTOR = 60
private const val SECTORS = 6
