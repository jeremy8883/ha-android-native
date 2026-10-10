package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.energy.StatisticPeriod
import io.homeassistant.companion.android.dashboard.energy.timeTicks
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The times a history chart's axis from [start] to [end] (epoch milliseconds) has labels at, about [maxLabels] of
 * them: whole minutes, as many apart as fit, over a span shorter than three hours (an entity created a few minutes
 * ago, as the frontend's time axis picks minutes then), else hours.
 */
fun DisplayFormats.historyTimeTicks(start: Long, end: Long, maxLabels: Int): List<Long> {
    if (end - start >= SHORT_SPAN_MILLIS) return timeTicks(start, end, StatisticPeriod.HOUR, maxLabels)
    val first = Instant.ofEpochMilli(start).atZone(zone).truncatedTo(ChronoUnit.MINUTES)
    val minutes = generateSequence(first) { it.plusMinutes(1) }
        .takeWhile { it.toInstant().toEpochMilli() <= end }
        .filter { it.toInstant().toEpochMilli() >= start }
        .toList()
    val step = MINUTE_STEPS.firstOrNull { (minutes.size + it - 1) / it <= maxLabels } ?: MINUTE_STEPS.last()
    return minutes.filter { it.minute % step == 0 }.map { it.toInstant().toEpochMilli() }
}

private const val SHORT_SPAN_MILLIS = 3 * 60 * 60 * 1000L
private val MINUTE_STEPS = listOf(1, 2, 5, 10, 15, 30, 60)
