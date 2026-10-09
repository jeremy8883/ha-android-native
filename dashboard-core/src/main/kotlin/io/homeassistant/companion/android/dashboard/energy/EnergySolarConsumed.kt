package io.homeassistant.companion.android.dashboard.energy

// How much of the solar production the home used. Port of `calculateSolarConsumedGauge` (frontend@20260624.6
// src/data/energy.ts).

/**
 * The share of the solar production the home used, following what charged the battery: what the battery gave back
 * comes last in, first out from what charged it, solar or grid. Port of `calculateSolarConsumedGauge`.
 */
fun solarConsumedGauge(hasBattery: Boolean, sums: EnergySums): Double? {
    val solar = sums.total[EnergyFlow.SOLAR]?.takeIf { it != 0.0 } ?: return null
    val consumption = sums.consumption()
    return if (hasBattery) {
        solarConsumedThroughBattery(sums, consumption)
    } else {
        consumption.total.usedSolar / solar *
            PERCENT
    }
}

/** The share of the solar production used, following the battery's charges last in, first out. */
private fun solarConsumedThroughBattery(sums: EnergySums, consumption: ConsumptionData): Double? {
    var consumed = 0.0
    var returned = 0.0
    val battery = ArrayDeque<Pair<Boolean, Double>>()
    sums.timestamps.forEach { t ->
        val period = consumption.byStart[t] ?: Consumption()
        consumed += period.usedSolar
        returned += period.solarToGrid
        if (period.gridToBattery != 0.0) battery.addLast(false to period.gridToBattery)
        if (period.solarToBattery != 0.0) battery.addLast(true to period.solarToBattery)
        consumed += battery.drain(period.usedBattery)
        returned += battery.drain(period.batteryToGrid)
    }
    val production = consumed + returned
    return if (production != 0.0) consumed / production * PERCENT else null
}

/** Takes [amount] from the last charges first, returning how much of it was solar. */
private fun ArrayDeque<Pair<Boolean, Double>>.drain(amount: Double): Double {
    var left = amount
    var solar = 0.0
    while (left > 0 && isNotEmpty()) {
        val (isSolar, stored) = last()
        val energy = if (left >=
            stored
        ) {
            stored.also { removeLast() }
        } else {
            left.also { this[lastIndex] = isSolar to stored - left }
        }
        if (isSolar) solar += energy
        left -= energy
    }
    return solar
}
