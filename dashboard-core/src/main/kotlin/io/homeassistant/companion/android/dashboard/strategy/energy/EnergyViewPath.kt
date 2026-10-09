package io.homeassistant.companion.android.dashboard.strategy.energy

/** The energy panel's views, by path. */
enum class EnergyViewPath(val path: String) {
    OVERVIEW("overview"),
    ELECTRICITY("electricity"),
    GAS("gas"),
    WATER("water"),
    NOW("now"),
}
