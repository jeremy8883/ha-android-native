package io.homeassistant.companion.android.dashboard.condition

/**
 * Evaluate a CSS media query for `screen` conditions, as the browser's `matchMedia` would.
 *
 * Supports what the Home Assistant condition editor produces and common hand-written queries: comma-separated
 * alternatives of `and`-joined `(min-width|max-width|min-height|max-height: Npx)` and `(orientation: …)`
 * features, optionally prefixed with `screen`/`all`. Anything else does not match.
 */
fun matchesMediaQuery(query: String, screen: ScreenInfo): Boolean =
    query.split(',').any { alternative -> matchesAlternative(alternative.trim().lowercase(), screen) }

private fun matchesAlternative(query: String, screen: ScreenInfo): Boolean {
    val parts = query.split(Regex("""\s+and\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return false
    return parts.all { part ->
        when {
            part == "screen" || part == "all" -> true
            part.startsWith("(") && part.endsWith(")") -> matchesFeature(part.removeSurrounding("(", ")"), screen)
            else -> false
        }
    }
}

private fun matchesFeature(feature: String, screen: ScreenInfo): Boolean {
    val parts = feature.split(':', limit = 2).map { it.trim() }
    if (parts.size != 2) return false
    val (name, value) = parts
    return if (name == "orientation") matchesOrientation(value, screen) else matchesSize(name, value, screen)
}

private fun matchesOrientation(value: String, screen: ScreenInfo): Boolean {
    val portrait = screen.heightDp >= screen.widthDp
    return when (value) {
        "portrait" -> portrait
        "landscape" -> !portrait
        else -> false
    }
}

private fun matchesSize(name: String, value: String, screen: ScreenInfo): Boolean {
    val px = value.removeSuffix("px").trim().toDoubleOrNull() ?: return false
    return when (name) {
        "min-width" -> screen.widthDp >= px
        "max-width" -> screen.widthDp <= px
        "min-height" -> screen.heightDp >= px
        "max-height" -> screen.heightDp <= px
        else -> false
    }
}
