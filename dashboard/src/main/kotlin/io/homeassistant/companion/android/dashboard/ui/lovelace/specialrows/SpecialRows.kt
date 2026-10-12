package io.homeassistant.companion.android.dashboard.ui.lovelace.specialrows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.EntitiesRow
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.lovelace.headerfooter.ButtonsBase
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint

/**
 * A special row of an entities card, inside the card's 16px content padding (which a section's line crosses).
 *
 * Upstream: `createRowElement` (src/panels/lovelace/create-element/create-row-element.ts)
 */
@Composable
internal fun SpecialRow(row: EntitiesRow, interactions: CardInteractions) {
    val padding = Modifier.fillMaxWidth().padding(horizontal = Css.Space.S4)
    when (row) {
        is EntitiesRow.Entity -> Unit
        EntitiesRow.Divider -> DividerRow(padding)
        is EntitiesRow.Section -> SectionRow(row, padding)
        is EntitiesRow.Weblink -> WeblinkRow(row, interactions, padding)
        is EntitiesRow.Button -> ButtonRow(row, interactions, padding)
        is EntitiesRow.Buttons -> ButtonsBase(row.buttons, BUTTONS_PADDING, BUTTONS_PADDING, interactions, padding)
        is EntitiesRow.Cast -> CastRow(row, padding)
        is EntitiesRow.Text -> TextRow(row, padding)
        is EntitiesRow.Invalid -> CardWarning(row.message, padding)
    }
}

/**
 * A 1px line across the row.
 *
 * Upstream: `hui-divider-row` (src/panels/lovelace/special-rows/hui-divider-row.ts)
 */
@Composable
private fun DividerRow(modifier: Modifier) {
    HAHorizontalDivider(modifier)
}

/**
 * A line across the whole card 8px down, then the label (medium weight, 16px down, 8px in) when it has one.
 *
 * Upstream: `hui-section-row` (src/panels/lovelace/special-rows/hui-section-row.ts)
 */
@Composable
private fun SectionRow(row: EntitiesRow.Section, modifier: Modifier) {
    Column(modifier) {
        // `.divider`'s `margin: 0 -16px`, out to the card's edges
        HAHorizontalDivider(Modifier.padding(top = Css.Space.S2).widenBy(Css.Space.S4))
        row.label?.let { label ->
            Text(
                label,
                style = Css.Text.Body.copy(fontWeight = Css.FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Css.Space.S2, top = Css.Space.S4, bottom = Css.Space.S2),
            )
        }
    }
}

/** Lays the element out [margin] wider on each side than its parent, as a negative CSS margin does. */
private fun Modifier.widenBy(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = margin.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * The link's icon and name in the link colour, opening it on tap.
 *
 * Upstream: `hui-weblink-row` (src/panels/lovelace/special-rows/hui-weblink-row.ts)
 */
@Composable
private fun WeblinkRow(row: EntitiesRow.Weblink, interactions: CardInteractions, modifier: Modifier) {
    IconNameRow(row.icon, modifier.clickable { interactions.onAction(row.action) }) {
        Text(
            row.name,
            style = Css.Text.Body.copy(textDecoration = TextDecoration.Underline),
            color = LocalHAColorScheme.current.colorTextLink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The icon and name, then a small filled button running the row's actions.
 *
 * Upstream: `hui-button-row`, `hui-call-service-row` (src/panels/lovelace/special-rows/hui-button-row.ts;
 * src/panels/lovelace/special-rows/hui-call-service-row.ts)
 */
@Composable
private fun ButtonRow(row: EntitiesRow.Button, interactions: CardInteractions, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    IconNameRow(row.icon, modifier) {
        Text(
            row.name,
            style = Css.Text.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // `ha-button` filled, size s: 32px high, 12px in from its sides, pill-shaped
        Box(
            Modifier
                .height(BUTTON_HEIGHT)
                .clip(CircleShape)
                .background(colors.colorFillPrimaryNormalResting)
                .elementGestures(row.actions, interactions)
                .padding(horizontal = Css.Space.S3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                row.label,
                style = Css.Text.Body.copy(fontWeight = Css.FontWeight.Action, lineHeight = BUTTON_LINE_HEIGHT),
                color = colors.colorOnPrimaryNormal,
                maxLines = 1,
            )
        }
    }
}

/**
 * The icon and name, then why it can't cast.
 *
 * Upstream: `hui-cast-row` (src/panels/lovelace/special-rows/hui-cast-row.ts)
 */
@Composable
private fun CastRow(row: EntitiesRow.Cast, modifier: Modifier) {
    IconNameRow(row.icon, modifier) {
        Text(
            row.name,
            style = Css.Text.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(row.status, style = Css.Text.Body, maxLines = 1)
    }
}

/**
 * The icon, the name, then the text at the end, sharing the width.
 *
 * Upstream: `hui-text-row` (src/panels/lovelace/special-rows/hui-text-row.ts)
 */
@Composable
private fun TextRow(row: EntitiesRow.Text, modifier: Modifier) {
    IconNameRow(row.icon, modifier) {
        Text(
            row.name,
            style = Css.Text.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.text,
            style = Css.Text.Body.copy(textAlign = TextAlign.End),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A special row's layout: its icon in `--state-icon-color` with 8px around it (a 40px square, kept without an
 * icon), then its content 16px further.
 *
 * Upstream: part of `hui-weblink-row`, `hui-button-row`, `hui-cast-row`, `hui-text-row`
 * (src/panels/lovelace/special-rows/hui-weblink-row.ts; src/panels/lovelace/special-rows/hui-button-row.ts;
 * src/panels/lovelace/special-rows/hui-cast-row.ts; src/panels/lovelace/special-rows/hui-text-row.ts)
 */
@Composable
private fun IconNameRow(icon: String?, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.heightIn(min = ICON_BOX),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ICON_BOX), contentAlignment = Alignment.Center) {
            icon?.let { DashboardIcon(it, entityIconTint(null), Modifier.size(ICON_SIZE)) }
        }
        content()
    }
}

/** `ha-icon`'s 24px with `padding: 8px`. */
private val ICON_SIZE = 24.cssPx
private val ICON_BOX = 40.cssPx
private val BUTTON_HEIGHT = 32.cssPx

/** `ha-button`'s `line-height: 1`. */
private val BUTTON_LINE_HEIGHT = Css.FontSize.M

/** `hui-buttons-base`'s default `--padding-top` and `--padding-bottom`. */
private val BUTTONS_PADDING = 8.cssPx
