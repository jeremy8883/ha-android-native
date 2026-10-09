package io.homeassistant.companion.android.dashboard.derive

/** A colour as the frontend expresses it, for the theme layer to resolve. */
sealed interface DisplayColor {
    /** A theme colour name such as `red` or `primary` (the frontend's `var(--red-color)`). */
    data class Theme(val name: String) : DisplayColor

    /** A CSS colour given literally, for example `#ff0000` or `rgb(255, 0, 0)`. */
    data class Literal(val css: String) : DisplayColor

    /**
     * The colour of an entity's state: the first of these theme variables that is defined, without the leading
     * `--` (for example `state-light-on-color`, `state-light-active-color`, `state-active-color`).
     */
    data class State(val variables: List<String>) : DisplayColor
}
