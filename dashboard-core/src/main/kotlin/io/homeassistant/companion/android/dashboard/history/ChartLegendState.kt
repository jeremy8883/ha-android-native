package io.homeassistant.companion.android.dashboard.history

/**
 * The series hidden after a tap on legend item [id] of a chart hiding [hidden]: shown again when hidden, else
 * hidden. Port of `_handleDatasetToggle` (src/components/chart/ha-chart-base.ts).
 */
fun legendToggled(hidden: Set<String>, id: String): Set<String> = if (id in hidden) hidden - id else hidden + id

/**
 * The series hidden after a long press on legend item [id] of a chart with the items [allIds], hiding [hidden]:
 * only [id] shown, or, when it already was the only one shown, all of them again. Port of `_soloLegend`
 * (src/components/chart/ha-chart-base.ts).
 */
fun legendSoloed(allIds: List<String>, hidden: Set<String>, id: String): Set<String> {
    val others = allIds.filter { it != id }
    val onlyVisible = id !in hidden && others.all { it in hidden }
    return if (onlyVisible) emptySet() else hidden + others - id
}

/**
 * How many legend items show before the "More" chip on a screen [widthDp] by [heightDp]: fewer on a phone
 * (`all and (max-width: 450px), all and (max-height: 500px)`). Port of `LEGEND_OVERFLOW_LIMIT` and
 * `LEGEND_OVERFLOW_LIMIT_MOBILE` (src/components/chart/ha-chart-base.ts).
 */
fun legendOverflowLimit(widthDp: Int, heightDp: Int): Int =
    if (widthDp <= MOBILE_MAX_WIDTH || heightDp <= MOBILE_MAX_HEIGHT) OVERFLOW_LIMIT_MOBILE else OVERFLOW_LIMIT

private const val OVERFLOW_LIMIT = 10
private const val OVERFLOW_LIMIT_MOBILE = 6
private const val MOBILE_MAX_WIDTH = 450
private const val MOBILE_MAX_HEIGHT = 500
