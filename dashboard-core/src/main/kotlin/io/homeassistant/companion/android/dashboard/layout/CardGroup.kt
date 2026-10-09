package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.conditionsMet
import io.homeassistant.companion.android.dashboard.derive.cardHidesItself
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.ViewType
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import kotlinx.serialization.json.JsonObject

/**
 * A group of cards rendered together: a section of a `sections` view, or all cards of other views.
 *
 * @property visibility the section's visibility conditions
 * @property disabled sections a strategy disabled (for example an empty `common-controls` with `hide_empty`)
 * @property grid whether cards are laid out in a section grid; otherwise they are stacked
 * @property columnSpan view columns the section spans (`column_span`)
 * @property rowSpan view rows the section spans (`row_span`)
 */
data class CardGroup(
    val cards: List<CardConfig>,
    val visibility: List<JsonObject> = emptyList(),
    val disabled: Boolean = false,
    val grid: Boolean = false,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
)

/**
 * The card groups of [view], in display order.
 *
 * Sections become one grid group each, and every other view type becomes a single stacked group.
 * Still to port: masonry column distribution (src/panels/lovelace/views/hui-masonry-view.ts).
 */
fun cardGroups(view: ViewConfig): List<CardGroup> = when (view.viewType) {
    ViewType.SECTIONS -> view.sections.map {
        CardGroup(
            cards = it.cards,
            visibility = it.visibility,
            disabled = it.json.boolean("disabled") == true,
            grid = true,
            // `column_span || 1`: missing, 0 and invalid values span one column
            columnSpan = it.json.number("column_span")?.toInt()?.takeIf { span -> span > 0 } ?: 1,
            rowSpan = it.json.number("row_span")?.toInt()?.takeIf { span -> span > 0 } ?: 1,
        )
    }
    else -> listOf(CardGroup(view.cards))
}

/**
 * The cards of [group] currently shown, or `null` when the whole group is hidden: when disabled, when its
 * visibility conditions fail, or when every card is hidden by its own visibility conditions.
 * Port of `_updateVisibility` in frontend@20260624.6 src/panels/lovelace/sections/hui-section.ts and the card
 * visibility check in src/panels/lovelace/cards/hui-card.ts, plus cards that hide themselves ([cardHidesItself]).
 */
fun HassSnapshot.visibleCards(group: CardGroup, context: ConditionContext): List<CardConfig>? {
    if (group.disabled || (group.visibility.isNotEmpty() && !conditionsMet(group.visibility, context))) return null
    val cards = group.cards.filter { card ->
        (card.visibility.isEmpty() || conditionsMet(card.visibility, context.copy(entityId = card.entity))) &&
            conditionalCardShown(card, context) &&
            !cardHidesItself(card)
    }
    return cards.takeUnless { group.cards.isNotEmpty() && it.isEmpty() }
}

/**
 * Whether a conditional card's conditions pass (other cards always pass). Port of `HuiConditionalBase`
 * (frontend@20260624.6 src/panels/lovelace/components/hui-conditional-base.ts): a failing conditional card hides
 * itself, and with it its card.
 */
fun HassSnapshot.conditionalCardShown(card: CardConfig, context: ConditionContext): Boolean {
    if (card.type != CONDITIONAL) return true
    val conditions = card.json.objects("conditions")
    return conditions.isEmpty() || conditionsMet(conditions, context)
}

/** The card a conditional card shows, or `null` for other cards. */
fun CardConfig.conditionalInnerCard(): CardConfig? =
    if (type == CONDITIONAL) json.obj("card")?.let(::CardConfig) else null

/** [cards] with the cards nested in them (conditional cards), for data that inner cards need. */
fun withNestedCards(cards: List<CardConfig>): List<CardConfig> =
    cards.flatMap { card -> listOf(card) + withNestedCards(listOfNotNull(card.conditionalInnerCard())) }

private const val CONDITIONAL = "conditional"
