package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.ViewType

/** A group of cards rendered together: a section of a `sections` view, or all cards of other views. */
data class CardGroup(val cards: List<CardConfig>)

/**
 * The card groups of [view], in display order.
 *
 * Basic version: sections become one group each, and every other view type becomes a single group.
 * Still to port: grid sizing (src/panels/lovelace/sections/hui-grid-section.ts), masonry column
 * distribution (src/panels/lovelace/views/hui-masonry-view.ts) and visibility conditions.
 */
fun cardGroups(view: ViewConfig): List<CardGroup> = when (view.viewType) {
    ViewType.SECTIONS -> view.sections.map { CardGroup(it.cards) }
    else -> listOf(CardGroup(view.cards))
}
