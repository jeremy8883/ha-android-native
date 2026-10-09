package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.DisplayFormats

/**
 * What the energy compare card says while comparing: the shown and the compared periods' dates, and the other way
 * to compare it offers. Port of `HuiEnergyCompareCard.render` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/hui-energy-compare-card.ts).
 *
 * @property switchTo the comparison the card's link switches to
 * @property switchKey the translation key of that link
 */
data class EnergyCompareModel(val shown: String, val compared: String, val switchTo: CompareMode, val switchKey: String)

/** The card for [collection], `null` when it isn't comparing (the card is then hidden). */
fun DisplayFormats.energyCompare(collection: EnergyCollection): EnergyCompareModel? {
    val mode = collection.compareMode ?: return null
    val compared = when (mode) {
        CompareMode.PREVIOUS -> collection.period.previous()
        CompareMode.YEAR_OVER_YEAR -> collection.period.yearBefore()
    }
    // Both ends only when the compared period spans several days, as upstream
    val severalDays = compared.dayDifference > 0
    fun dates(period: EnergyPeriod) = date(period.startInstant(zone)) +
        if (severalDays) " - " + date(period.endInstant(zone)) else ""
    return EnergyCompareModel(
        shown = dates(collection.period),
        compared = dates(compared),
        switchTo = if (mode == CompareMode.PREVIOUS) CompareMode.YEAR_OVER_YEAR else CompareMode.PREVIOUS,
        switchKey = if (mode == CompareMode.PREVIOUS) "compare_previous_year" else "compare_previous_period",
    )
}
