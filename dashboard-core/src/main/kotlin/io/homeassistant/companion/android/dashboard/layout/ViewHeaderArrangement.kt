package io.homeassistant.companion.android.dashboard.layout

/**
 * How a view's header places its card and badges, from its `layout`, `badges_position` and `badges_wrap` and the
 * screen's width. Port of `hui-view-header`'s and `hui-view-badges`' styles (frontend@20260624.6
 * src/panels/lovelace/views/hui-view-header.ts, src/panels/lovelace/badges/hui-view-badges.ts).
 */
data class ViewHeaderArrangement(val flow: HeaderFlow, val badges: BadgesArrangement)

/** Where the header's card and badges go relative to each other. */
sealed interface HeaderFlow {
    /**
     * The card over the badges (under them when [badgesFirst]), both at the start or centred, with the card's text
     * centred too when [centered].
     */
    data class Stacked(val centered: Boolean, val badgesFirst: Boolean) : HeaderFlow

    /** The card then the badges on one row (`responsive` on a wide screen), aligned on their tops or bottoms. */
    data class SideBySide(val alignTop: Boolean) : HeaderFlow
}

/** How the badges are laid out: wrapped onto several lines, or one line that scrolls ([scroll]). */
data class BadgesArrangement(val scroll: Boolean, val alignment: BadgesAlignment)

/** Where the badges go along their line (`--badges-aligmnent`). */
sealed interface BadgesAlignment {
    data object Start : BadgesAlignment
    data object Center : BadgesAlignment
    data object End : BadgesAlignment

    /** Spread with equal room around each, while they fit. */
    data object SpaceAround : BadgesAlignment
}

/**
 * The arrangement of a header with [layout], [badgesPosition] and [badgesWrap] (their config values), with or
 * without a card ([hasCard]), on a screen [widthDp] wide (`null` when unknown, taken as narrow).
 */
fun viewHeaderArrangement(
    layout: String,
    badgesPosition: String,
    badgesWrap: String,
    hasCard: Boolean,
    widthDp: Int?,
): ViewHeaderArrangement {
    val wide = widthDp != null && widthDp >= RESPONSIVE_MIN_WIDTH_DP
    if (layout == LAYOUT_RESPONSIVE && hasCard && wide) {
        // Side by side, the badges always wrap, at the end
        return ViewHeaderArrangement(
            flow = HeaderFlow.SideBySide(alignTop = badgesPosition == BADGES_TOP),
            badges = BadgesArrangement(scroll = false, alignment = BadgesAlignment.End),
        )
    }
    val centered = layout == LAYOUT_CENTER
    val scroll = badgesWrap == BADGES_SCROLL
    val alignment = when {
        scroll && centered -> BadgesAlignment.SpaceAround
        centered -> BadgesAlignment.Center
        else -> BadgesAlignment.Start
    }
    return ViewHeaderArrangement(
        flow = HeaderFlow.Stacked(centered = centered, badgesFirst = badgesPosition == BADGES_TOP),
        badges = BadgesArrangement(scroll = scroll, alignment = alignment),
    )
}

/** The `@media (min-width: 768px)` from which a `responsive` header puts its card and badges side by side. */
private const val RESPONSIVE_MIN_WIDTH_DP = 768
private const val LAYOUT_CENTER = "center"
private const val LAYOUT_RESPONSIVE = "responsive"
private const val BADGES_TOP = "top"
private const val BADGES_SCROLL = "scroll"
