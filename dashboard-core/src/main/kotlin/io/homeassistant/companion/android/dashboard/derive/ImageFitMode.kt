package io.homeassistant.companion.android.dashboard.derive

/** How a picture fills its box (`fit_mode` of `hui-image`). */
enum class ImageFitMode {
    /** Cropped to fill the box, the default. */
    COVER,

    /** Whole, letterboxed. */
    CONTAIN,

    /** Stretched to the box. */
    FILL,
}

/** The [ImageFitMode] a config's `fit_mode` names. */
fun imageFitMode(value: String?): ImageFitMode = when (value) {
    "contain" -> ImageFitMode.CONTAIN
    "fill" -> ImageFitMode.FILL
    else -> ImageFitMode.COVER
}

/**
 * Width over height of an `aspect_ratio` such as `16x9`, `16:9`, `1.78x1`, `1.78` or `56.25%` (height as a
 * percentage of the width), or `null` when it isn't one or isn't positive (`hui-image` then doesn't use it).
 *
 * Port of `parseAspectRatio` (frontend@20260624.6 src/common/util/parse-aspect-ratio.ts) and `hui-image`'s
 * `useRatio` (src/panels/lovelace/components/hui-image.ts).
 */
fun parseAspectRatio(input: String?): Double? {
    if (input.isNullOrEmpty()) return null
    val (width, height) = if (input.endsWith("%")) {
        PERCENT to jsParseFloat(input.dropLast(1))
    } else {
        val parts = input.replaceFirst(":", "x").split("x")
        jsParseFloat(parts[0]) to (if (parts.size == 1) 1.0 else jsParseFloat(parts[1]))
    }
    return if (listOf(width, height).all { it > 0 }) width / height else null
}

private const val PERCENT = 100.0
