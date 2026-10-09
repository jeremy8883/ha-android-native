package io.homeassistant.companion.android.dashboard.energy

/**
 * An energy collection as its cards see it, the counterpart of the frontend's `EnergyCollection`
 * (frontend@20260624.6 src/data/energy.ts): the period and comparison chosen with the date selection, and the data.
 *
 * @property data the latest data loaded, which is still the previous period's while [loading] the chosen one, and
 * `null` until the first is loaded
 * @property loading whether the chosen period's data is being loaded
 * @property failed whether loading the chosen period's data failed
 */
data class EnergyCollection(
    val period: EnergyPeriod,
    val compareMode: CompareMode?,
    val data: EnergyData?,
    val loading: Boolean,
    val failed: Boolean,
)
