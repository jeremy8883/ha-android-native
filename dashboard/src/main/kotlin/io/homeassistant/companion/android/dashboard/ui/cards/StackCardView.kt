package io.homeassistant.companion.android.dashboard.ui.cards

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.StackArrangement
import io.homeassistant.companion.android.dashboard.layout.StackCard
import io.homeassistant.companion.android.dashboard.layout.visibleStackCards
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.ZonedDateTime

/**
 * A vertical stack, horizontal stack or grid card, port of `HuiStackCard` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-stack-card.ts and its subclasses): its title, then its shown cards one under the
 * other, side by side, or in columns, 8 apart.
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
            StackArrangement.Vertical -> Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                cards.forEach { key(it) { card(it, Modifier.fillMaxWidth()) } }
            }
            StackArrangement.Horizontal -> Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                cards.forEach { key(it) { card(it, Modifier.weight(1f)) } }
            }
            is StackArrangement.Grid -> StackGrid(cards, arrangement, card)
        }
    }
}

/** The cards in [grid]'s columns, a cell at least as tall as wide when square. */
@Composable
private fun StackGrid(
    cards: List<CardConfig>,
    grid: StackArrangement.Grid,
    card: @Composable (CardConfig, Modifier) -> Unit,
) {
    val cell = if (grid.square) Modifier.atLeastAsTallAsWide() else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        cards.chunked(grid.columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
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

@Composable
private fun StackTitle(title: String) {
    Text(
        title,
        // `--ha-font-size-2xl` at the normal weight
        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Normal),
        color = LocalHAColorScheme.current.colorTextPrimary,
        modifier = Modifier.fillMaxWidth().padding(
            start = HADimens.SPACE4,
            top = HADimens.SPACE6,
            end = HADimens.SPACE4,
            bottom = HADimens.SPACE4,
        ),
    )
}
