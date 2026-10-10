package io.homeassistant.companion.android.dashboard.derive

/** A colour as the frontend expresses it, for the theme layer to resolve. */
sealed interface DisplayColor {
    /** A theme colour name such as `red` or `primary` (the frontend's `var(--red-color)`). */
    data class Theme(val name: String) : DisplayColor

    /** A CSS colour given literally, for example `#ff0000` or `rgb(255, 0, 0)`. */
    data class Literal(val css: String) : DisplayColor

    /**
     * The colour of an entity's state: the first of these theme variables that is defined, without the leading
     * `--` (for example `state-light-on-color`, `state-light-active-color`, `state-active-color`). [overrides]
     * redefine some of them, as a control's style sets a variable for its children, and [unset] ones are set to
     * `initial`, which leaves the colour unresolved when the lookup reaches them.
     */
    data class State(
        val variables: List<String>,
        val overrides: Map<String, DisplayColor> = emptyMap(),
        val unset: Set<String> = emptySet(),
    ) : DisplayColor
}

/**
 * This colour as `state-badge` and heading badges draw it: with `--state-inactive-color: initial`, so an inactive
 * state without a colour of its own shows the element's colour.
 */
fun DisplayColor.withInactiveUnset(): DisplayColor =
    if (this is DisplayColor.State) copy(unset = unset + INACTIVE_COLOR) else this

/** This colour as the button and entity cards draw it: inactive is the icon colour (`--state-icon-color`). */
fun DisplayColor.withInactiveAsIcon(): DisplayColor = if (this is DisplayColor.State) {
    copy(overrides = overrides + (INACTIVE_COLOR to DisplayColor.State(listOf(ICON_COLOR))))
} else {
    this
}

/** The default colour of entity icons, `--state-icon-color`. */
val STATE_ICON_COLOR: DisplayColor = DisplayColor.State(listOf("state-icon-color"))

private const val INACTIVE_COLOR = "state-inactive-color"
private const val ICON_COLOR = "state-icon-color"
