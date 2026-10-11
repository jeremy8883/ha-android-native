package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import java.time.Instant

/**
 * What a timeline band's tooltip says (`_renderTooltip` of src/components/chart/state-history-chart-timeline.ts):
 * the entity's [title] (only when the chart names its rows), the band's [state], when it [started][start] and
 * [ended][end] to the second, and its [duration] line.
 */
data class TimelineTooltip(
    val title: String?,
    val state: String,
    val start: String,
    val end: String,
    val duration: String,
)

/**
 * The tooltip of [band] of [timeline], its entity named when [showNames], the duration line led by
 * [durationLabel] (`ui.components.history_charts.duration`).
 */
fun DisplayFormats.timelineTooltip(
    timeline: TimelineChart,
    band: TimelineBand,
    showNames: Boolean,
    durationLabel: String,
): TimelineTooltip {
    val millis = (band.end - band.start).toLong()
    return TimelineTooltip(
        title = timeline.name.takeIf { showNames && it.isNotEmpty() },
        state = band.label,
        start = dateTimeWithSeconds(Instant.ofEpochMilli(band.start.toLong())),
        end = dateTimeWithSeconds(Instant.ofEpochMilli(band.end.toLong())),
        // Upstream writes "null" for a band without length, which a tap can't select
        duration = "$durationLabel: ${millisecondsToDuration(millis)}",
    )
}

/** Port of `millisecondsToDuration` (src/common/datetime/milliseconds_to_duration.ts): "1:02:03", "2:03", "3.250". */
fun millisecondsToDuration(millis: Long): String? {
    val seconds = millis / MILLIS
    val h = seconds / HOUR
    val m = seconds % HOUR / MINUTE
    val s = seconds % HOUR % MINUTE
    val ms = millis % MILLIS
    val pad = { value: Long, digits: Int -> value.toString().padStart(digits, '0') }
    return when {
        h > 0 -> "$h:${pad(m, 2)}:${pad(s, 2)}"
        m > 0 -> "$m:${pad(s, 2)}"
        s > 0 || ms > 0 -> if (ms > 0) "$s.${pad(ms, MS_DIGITS)}" else "$s"
        else -> null
    }
}

private const val MILLIS = 1000L
private const val HOUR = 3600L
private const val MINUTE = 60L
private const val MS_DIGITS = 3
