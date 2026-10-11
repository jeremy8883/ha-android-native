package io.homeassistant.companion.android.dashboard.ui.lovelace.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.BadgesAlignment
import io.homeassistant.companion.android.dashboard.layout.BadgesArrangement
import io.homeassistant.companion.android.dashboard.layout.HeaderFlow
import io.homeassistant.companion.android.dashboard.layout.ViewBadgeModel
import io.homeassistant.companion.android.dashboard.layout.viewHeader
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.DashboardCard
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalCardTextAlign
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * The header of a sections view: its card and its badges, placed as its `layout`, `badges_position` and
 * `badges_wrap` say.
 *
 * Upstream: `hui-view-header` (src/panels/lovelace/views/hui-view-header.ts)
 */
@Composable
internal fun ViewHeader(
    view: ViewConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val context = LocalConditionContext.current
    val model by remember(view, context) {
        derivedStateOf {
            hass.value?.viewHeader(view, context.copy(now = now.value), now.value?.toInstant() ?: Instant.EPOCH)
        }
    }
    val header = model ?: return
    val arrangement = header.arrangement
    val card = header.card?.let { card ->
        @Composable { modifier: Modifier -> DashboardCard(card, hass, now, interactions, modifier) }
    }
    val badges = header.badges.takeIf { it.isNotEmpty() }?.let { badges ->
        @Composable { modifier: Modifier -> ViewBadges(badges, arrangement.badges, interactions, modifier) }
    }
    when (val flow = arrangement.flow) {
        is HeaderFlow.Stacked -> Column(
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            horizontalAlignment = if (flow.centered) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val textAlign = if (flow.centered) TextAlign.Center else TextAlign.Start
            if (flow.badgesFirst) badges?.invoke(Modifier.fillMaxWidth())
            CompositionLocalProvider(LocalCardTextAlign provides textAlign) {
                card?.invoke(Modifier.widthIn(max = HEADING_MAX_WIDTH).fillMaxWidth())
            }
            if (!flow.badgesFirst) badges?.invoke(Modifier.fillMaxWidth())
        }
        is HeaderFlow.SideBySide -> Row(
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            verticalAlignment = if (flow.alignTop) Alignment.Top else Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            card?.invoke(Modifier.weight(1f).widthIn(max = HEADING_MAX_WIDTH))
            badges?.invoke(Modifier.weight(1f))
        }
    }
}

/**
 * The view's badges: wrapped onto lines, or on one line that scrolls, fading at its edges.
 *
 * Upstream: `hui-view-badges` (src/panels/lovelace/badges/hui-view-badges.ts)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewBadges(
    badges: List<ViewBadgeModel>,
    arrangement: BadgesArrangement,
    interactions: CardInteractions,
    modifier: Modifier,
) {
    val alignment = when (arrangement.alignment) {
        BadgesAlignment.Start, BadgesAlignment.SpaceAround -> Alignment.Start
        BadgesAlignment.Center -> Alignment.CenterHorizontally
        BadgesAlignment.End -> Alignment.End
    }
    if (!arrangement.scroll) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, alignment),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            badges.forEach { ViewBadge(it, interactions) }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fadingEdges(SCROLL_FADE)) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth)
                .padding(horizontal = SCROLL_PADDING),
            horizontalArrangement = if (arrangement.alignment == BadgesAlignment.SpaceAround) {
                SpaceAroundAtLeast(HADimens.SPACE2)
            } else {
                Arrangement.spacedBy(HADimens.SPACE2, alignment)
            },
            verticalAlignment = Alignment.Top,
        ) {
            badges.forEach { ViewBadge(it, interactions) }
        }
    }
}

/**
 * A badge: its icon, then its name (small) over its state, on a card-coloured pill with a border; just the icon in
 * a circle when it shows neither.
 *
 * Upstream: `hui-entity-badge`, `ha-badge` (src/panels/lovelace/badges/hui-entity-badge.ts; src/components/ha-badge.ts)
 */
@Composable
private fun ViewBadge(badge: ViewBadgeModel, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val iconOnly = badge.label == null && badge.content == null
    val shape = CircleShape
    Row(
        modifier = Modifier
            .height(BADGE_SIZE)
            .widthIn(min = BADGE_SIZE)
            .clip(shape)
            .background(DisplayColor.State(listOf(CARD_BACKGROUND)).toColor() ?: colors.colorSurfaceDefault)
            .border(BADGE_BORDER, colors.colorBorderNeutralQuiet, shape)
            .elementGestures(badge.actions, interactions)
            .padding(
                // The icon's -4px margin pulls it towards the start
                start = if (iconOnly) {
                    HADimens.SPACE0
                } else if (badge.icon != null) {
                    HADimens.SPACE2
                } else {
                    HADimens.SPACE3
                },
                end = if (iconOnly) HADimens.SPACE0 else HADimens.SPACE3,
            ),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(
            name = badge.icon,
            // `--badge-color`: the primary colour while active, else the inactive one
            tint = badge.color?.toColor()
                ?: DisplayColor.State(listOf(if (badge.active) "primary-color" else "state-inactive-color")).toColor()
                ?: colors.colorTextSecondary,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
        if (!iconOnly) BadgeInfo(badge.label, badge.content)
    }
}

/**
 * A badge's text: the label, small and secondary, over the content.
 *
 * Upstream: part of `ha-badge` (src/components/ha-badge.ts)
 */
@Composable
private fun BadgeInfo(label: String?, content: String?) {
    val colors = LocalHAColorScheme.current
    val base = HATextStyle.BodyMedium.copy(
        textAlign = TextAlign.Start,
        fontWeight = FontWeight.Medium,
        letterSpacing = BADGE_LETTER_SPACING,
    )
    Column(horizontalAlignment = Alignment.Start) {
        label?.let {
            Text(
                it,
                style = base.copy(fontSize = HAFontSize.XS, lineHeight = HAFontSize.XS),
                color = colors.colorTextSecondary,
                maxLines = 1,
            )
        }
        content?.let {
            Text(
                it,
                style = base.copy(fontSize = HAFontSize.S, lineHeight = BADGE_CONTENT_LINE_HEIGHT),
                color = colors.colorTextPrimary,
                maxLines = 1,
            )
        }
    }
}

/** Fades the start and end [width] of the content, as upstream's `mask-image` on scrolling badges. */
private fun Modifier.fadingEdges(width: Dp): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = (width.toPx() / size.width).coerceAtMost(HALF)
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            fade to Color.Black,
            1f - fade to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * `justify-content: space-around` with a `gap`: equal room around each item while they fit, at least [gap] apart.
 */
private class SpaceAroundAtLeast(private val gap: Dp) : Arrangement.Horizontal {
    override val spacing: Dp = gap

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        if (sizes.isEmpty()) return
        val gapPx = gap.roundToPx()
        val used = sizes.sum() + gapPx * (sizes.size - 1)
        val around = (totalSize - used).coerceAtLeast(0).toFloat() / sizes.size
        var x = around / 2
        sizes.forEachIndexed { index, size ->
            val start = x.roundToInt()
            outPositions[index] = if (layoutDirection == LayoutDirection.Rtl) totalSize - start - size else start
            x += size + gapPx + around
        }
    }
}

private const val CARD_BACKGROUND = "card-background-color"
private const val HALF = 0.5f

/** `.heading`'s `max-width`. */
private val HEADING_MAX_WIDTH = 700.dp

/** `--ha-badge-size`. */
private val BADGE_SIZE = 36.dp

/** `--ha-badge-icon-size`. */
private val BADGE_ICON_SIZE = 18.dp

/** `--ha-card-border-width`. */
private val BADGE_BORDER = 1.dp
private val BADGE_LETTER_SPACING = 0.1.sp

/** `--ha-line-height-condensed` (1.2) of the 12px content. */
private val BADGE_CONTENT_LINE_HEIGHT = 14.4.sp

/** `--badge-padding` of scrolling badges. */
private val SCROLL_PADDING = 16.dp

/** The scrolling badges' `mask-image` fade. */
private val SCROLL_FADE = 16.dp
