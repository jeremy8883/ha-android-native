package io.homeassistant.companion.android.dashboard.derive

/** How an `ha-button` is filled (its `appearance` attribute). */
enum class ButtonAppearance {
    /** Loud fill, the default. */
    ACCENT,

    /** Normal (quieter) fill. */
    FILLED,

    /** No fill, text only. */
    PLAIN,
}

/** The colour family of an `ha-button` (its `variant` attribute). */
enum class ButtonColorVariant {
    BRAND,
    NEUTRAL,
    SUCCESS,
    WARNING,
    DANGER,
}

/**
 * The [ButtonAppearance] named by a config's `appearance`; webawesome's button falls back to accent.
 *
 * Port of `ha-button`'s `appearance` attribute (frontend@20260624.6 src/components/ha-button.ts).
 */
fun buttonAppearance(value: String?): ButtonAppearance = when (value) {
    "filled" -> ButtonAppearance.FILLED
    "plain" -> ButtonAppearance.PLAIN
    else -> ButtonAppearance.ACCENT
}

/**
 * The [ButtonColorVariant] named by a config's `variant`; `ha-button` defaults to brand.
 *
 * Port of `ha-button`'s `variant` attribute (frontend@20260624.6 src/components/ha-button.ts).
 */
fun buttonColorVariant(value: String?): ButtonColorVariant = when (value) {
    "neutral" -> ButtonColorVariant.NEUTRAL
    "success" -> ButtonColorVariant.SUCCESS
    "warning" -> ButtonColorVariant.WARNING
    "danger" -> ButtonColorVariant.DANGER
    else -> ButtonColorVariant.BRAND
}
