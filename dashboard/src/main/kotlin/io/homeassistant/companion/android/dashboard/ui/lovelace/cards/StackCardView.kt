package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.em
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.StackArrangement
import io.homeassistant.companion.android.dashboard.layout.StackCard
import io.homeassistant.companion.android.dashboard.layout.visibleStackCards
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.DashboardCard
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.ZonedDateTime

/**
 * A vertical stack, horizontal stack or grid card, port of `HuiStackCard` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-stack-card.ts and its subclasses): its title, then its shown cards one under the
 * other, side by side, or in columns, 8 apart.
 *
 * Upstream: `HuiStackCard`, `hui-vertical-stack-card`, `hui-horizontal-stack-card`, `hui-grid-card`
 * (src/panels/lovelace/cards/hui-stack-card.ts; src/panels/lovelace/cards/hui-vertical-stack-card.ts;
 * src/panels/lovelace/cards/hui-horizontal-stack-card.ts; src/panels/lovelace/cards/hui-grid-card.ts)
 */
@Composable
internal fun StackCardView(
    stack: StackCard,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val context = LocalConditionContext.current
    val shown by remember(stack, context) {
        derivedStateOf { hass.value?.visibleStackCards(stack, context.copy(now = now.value)) }
    }
    val cards = shown ?: return
    val card: @Composable (CardConfig, Modifier) -> Unit = { config, cardModifier ->
        DashboardCard(config, hass, now, interactions, cardModifier)
    }
    Column(modifier) {
        stack.title?.let { StackTitle(it) }
        when (val arrangement = stack.arrangement) {
            StackArrangement.Vertical -> Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
                cards.forEach { key(it) { card(it, Modifier.fillMaxWidth()) } }
            }
            StackArrangement.Horizontal -> Row(horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
                cards.forEach { key(it) { card(it, Modifier.weight(1f)) } }
            }
            is StackArrangement.Grid -> StackGrid(cards, arrangement, card)
        }
    }
}

/**
 * The cards in [grid]'s columns, a cell at least as tall as wide when square.
 *
 * Upstream: part of `hui-grid-card` (src/panels/lovelace/cards/hui-grid-card.ts)
 */
@Composable
private fun StackGrid(
    cards: List<CardConfig>,
    grid: StackArrangement.Grid,
    card: @Composable (CardConfig, Modifier) -> Unit,
) {
    val cell = if (grid.square) Modifier.atLeastAsTallAsWide() else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        cards.chunked(grid.columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
                row.forEach { key(it) { card(it, Modifier.weight(1f).then(cell)) } }
                // Keep the last row's cells as wide as the others
                repeat(grid.columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** At least as tall as the width it's given, like the grid's square rows. */
private fun Modifier.atLeastAsTallAsWide(): Modifier = layout { measurable, constraints ->
    val minHeight = constraints.maxWidth.coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = minHeight))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * A stack's title, as the card header above its cards.
 *
 * Upstream: part of `HuiStackCard` (src/panels/lovelace/cards/hui-stack-card.ts)
 */
@Composable
private fun StackTitle(title: String) {
    Text(
        title,
        // The stack's own `.card-header`, at `--ha-line-height-condensed`
        style = Css.Text.CardHeader.copy(lineHeight = STACK_TITLE_LINE_HEIGHT.em),
        modifier = Modifier.fillMaxWidth().padding(
            start = Css.Space.S4,
            top = Css.Space.S6,
            end = Css.Space.S4,
            bottom = Css.Space.S4,
        ),
    )
}

/** `--ha-line-height-condensed`. */
private const val STACK_TITLE_LINE_HEIGHT = 1.2f
