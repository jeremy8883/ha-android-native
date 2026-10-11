package io.homeassistant.companion.android.dashboard.moreinfo

/** The line under a round control's big number (`ha-state-control-climate-temperature`'s `show-secondary`). */
sealed interface CircularSecondary {
    /** The current temperature, after a thermometer. */
    data class Current(val text: String) : CircularSecondary

    /**
     * The targets as the user sets them, after a thermostat: the single one with its [number]'s unit, or (a [range])
     * the low and high ends without it, each choosing which one the buttons change. [number] gives the unit and the
     * decimals.
     */
    data class Targets(val number: BigNumber, val range: Boolean) : CircularSecondary
}
