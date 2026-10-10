package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.DisplayColor

// The round controls of the more-info dialog: `ha-control-circular-slider` (frontend@20260624.6
// src/components/ha-control-circular-slider.ts) with the number and buttons the state controls put inside
// (src/state-control/state-control-circular-slider-style.ts).

/** Which part of the arc a single-value [CircularSlider] colours. */
sealed interface CircularMode {
    /** From the start to the value (heating). */
    data object Start : CircularMode

    /** From the value to the end (cooling). */
    data object End : CircularMode

    /** The whole arc. */
    data object Full : CircularMode
}

/**
 * Port of `ha-control-circular-slider`: a 270° arc with a value (or a low and high pair, when [dual]) and the
 * current reading.
 *
 * @property inactive whether the entity is off: the coloured arcs hide, the value stays
 * @property readonly whether it only shows [current]
 * @property disabled whether nothing but the track and [current] shows
 * @property color the arc of a single value; [lowColor] and [highColor] those of a pair
 * @property actionColor the glow behind it while the entity is heating, cooling and so on
 */
data class CircularSlider(
    val mode: CircularMode,
    val dual: Boolean,
    val value: Double?,
    val low: Double?,
    val high: Double?,
    val current: Double?,
    val min: Double,
    val max: Double,
    val step: Double,
    val inactive: Boolean,
    val readonly: Boolean,
    val disabled: Boolean,
    val color: DisplayColor?,
    val lowColor: DisplayColor?,
    val highColor: DisplayColor?,
    val actionColor: DisplayColor?,
)

/** Port of `ha-big-number`: a number with [fractionDigits] decimals and its [unit]. */
data class BigNumber(val value: Double, val unit: String, val fractionDigits: Int)

/** What the middle of a round control shows. */
sealed interface CircularPrimary {
    /** The target. */
    data class Target(val number: BigNumber) : CircularPrimary

    /** The low and high targets, side by side; the selected one is the buttons'. */
    data class Range(val low: BigNumber, val high: BigNumber) : CircularPrimary

    /** The state, when there is no target. */
    data class Text(val text: String) : CircularPrimary

    /** Nothing (the entity is unavailable). */
    data object None : CircularPrimary
}

/** Which target a round control's buttons and drags change. */
sealed interface CircularTarget {
    /** The single target. */
    data object Value : CircularTarget

    /** The low end of a range. */
    data object Low : CircularTarget

    /** The high end of a range. */
    data object High : CircularTarget
}

/**
 * A round state control: the [slider], the [label] above the number (in the action's colour; greyed when
 * [labelDisabled]), the [primary] number, and minus/plus [buttons] by [step] when it can be changed.
 *
 * @property lowButtonColor the buttons' outline for the low target of a range (`null` for the default)
 * @property highButtonColor the buttons' outline for the high target of a range
 */
data class CircularControl(
    val slider: CircularSlider,
    val label: String?,
    val labelDisabled: Boolean,
    val primary: CircularPrimary,
    val buttons: Boolean,
    val lowButtonColor: DisplayColor?,
    val highButtonColor: DisplayColor?,
)

/** A round control's targets as the user sets them: [value], or [low] and [high]. */
data class CircularTargets(val value: Double?, val low: Double?, val high: Double?) {
    /** [target]'s value. */
    operator fun get(target: CircularTarget): Double? = when (target) {
        CircularTarget.Value -> value
        CircularTarget.Low -> low
        CircularTarget.High -> high
    }

    /** These, with [target] set to [to]. */
    fun with(target: CircularTarget, to: Double): CircularTargets = when (target) {
        CircularTarget.Value -> copy(value = to)
        CircularTarget.Low -> copy(low = to)
        CircularTarget.High -> copy(high = to)
    }

    /**
     * Port of the state controls' `_handleButton`: [target] moved by [step] within [min] and [max], and within the
     * other end of a range. From nothing, it starts at [min] (at [max] for the high end).
     */
    fun stepped(target: CircularTarget, step: Double, min: Double, max: Double): CircularTargets {
        val start = get(target) ?: if (target == CircularTarget.High) max else min
        var next = clamp(start + step, min, max)
        if (target == CircularTarget.High) low?.let { next = clamp(next, it, max) }
        if (target == CircularTarget.Low) high?.let { next = clamp(next, min, it) }
        return with(target, next)
    }
}

/** Port of `clamp`, which (unlike `coerceIn`) takes a [min] above [max]: the result is then [max]. */
internal fun clamp(value: Double, min: Double, max: Double): Double = minOf(maxOf(value, min), max)
