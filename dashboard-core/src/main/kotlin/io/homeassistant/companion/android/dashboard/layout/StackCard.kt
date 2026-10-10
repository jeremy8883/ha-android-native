package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray

/** How a stack card lays out its cards. */
sealed interface StackArrangement {
    /** One under the other (`vertical-stack`). */
    data object Vertical : StackArrangement

    /** Side by side, sharing the width (`horizontal-stack`). */
    data object Horizontal : StackArrangement

    /** In [columns] equal columns, each cell at least as tall as wide when [square] (`grid`). */
    data class Grid(val columns: Int, val square: Boolean) : StackArrangement
}

/**
 * A card holding other cards: an optional [title] above [cards] laid out by [arrangement]. Port of `HuiStackCard`
 * and its vertical, horizontal and grid cards (frontend@20260624.6 src/panels/lovelace/cards/hui-stack-card.ts,
 * hui-vertical-stack-card.ts, hui-horizontal-stack-card.ts, hui-grid-card.ts).
 */
data class StackCard(val title: String?, val arrangement: StackArrangement, val cards: List<CardConfig>)

/** This card as a stack, or `null` when it isn't one (or has no `cards` list, which upstream rejects). */
fun CardConfig.stackCard(): StackCard? {
    val arrangement = when (type) {
        VERTICAL_STACK -> StackArrangement.Vertical
        HORIZONTAL_STACK -> StackArrangement.Horizontal
        GRID -> StackArrangement.Grid(
            // `columns || 3`
            columns = json.number("columns")?.toInt()?.takeIf { it > 0 } ?: DEFAULT_GRID_COLUMNS,
            square = json.boolean("square") != false,
        )
        else -> null
    }
    return arrangement?.takeIf { json["cards"] is JsonArray }?.let {
        StackCard(
            title = json.string("title")?.ifEmpty {
                null
            },
            arrangement = it,
            cards = json.objects("cards").map(::CardConfig),
        )
    }
}

/** The cards of [stack] shown (see [cardShown]). */
fun HassSnapshot.visibleStackCards(stack: StackCard, context: ConditionContext): List<CardConfig> =
    stack.cards.filter { cardShown(it, context) }

private const val VERTICAL_STACK = "vertical-stack"
private const val HORIZONTAL_STACK = "horizontal-stack"
private const val GRID = "grid"
private const val DEFAULT_GRID_COLUMNS = 3
