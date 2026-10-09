package io.homeassistant.companion.android.dashboard.theme

/**
 * The value of the frontend colour variable [name] in its default theme (the dark one's first when [dark]) as an
 * ARGB colour, following references to other variables; `null` when the theme doesn't define it.
 */
fun themeColorArgb(name: String, dark: Boolean): Long? =
    generateSequence(name) { variable -> (frontendColor(variable, dark) as? FrontendColor.Ref)?.variable }
        .take(MAX_REFERENCE_DEPTH)
        .firstNotNullOfOrNull { variable -> (frontendColor(variable, dark) as? FrontendColor.Hex)?.argb }

/** The frontend's value of [variable], the dark theme's first when [dark]. */
private fun frontendColor(variable: String, dark: Boolean): FrontendColor? =
    (if (dark) FRONTEND_DARK_COLORS[variable] else null) ?: FRONTEND_LIGHT_COLORS[variable]

private const val MAX_REFERENCE_DEPTH = 8
