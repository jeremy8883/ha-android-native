package io.homeassistant.companion.android.dashboard.ui.panels.logbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.logbook.CauseBadge
import io.homeassistant.companion.android.dashboard.logbook.LogbookCause
import io.homeassistant.companion.android.dashboard.logbook.LogbookDot
import io.homeassistant.companion.android.dashboard.logbook.LogbookRow
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.ServerImage
import io.homeassistant.companion.android.dashboard.ui.components.chart.TimelineColors
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

// The parts of a logbook's rows that the details' logbook and the Logbook panel share: the dot on its day's rail,
// the cause's badge, the day headers and the messages in place of rows. Ports of `ha-logbook-entry`'s
// (frontend@20260624.6 src/panels/logbook/ha-logbook-entry.ts).

/**
 * The row's dot, with the day's rail through it (from the day's first dot to its last): in the middle of the row,
 * or [dotCentre] from its top (beside a named row's first line).
 *
 * Upstream: part of `ha-logbook-entry` (src/panels/logbook/ha-logbook-entry.ts)
 */
@Composable
internal fun LogbookNode(row: LogbookRow, modifier: Modifier, dotCentre: Dp? = null) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val railColor = colors.colorBorderNeutralQuiet
    val dotColor = when (val dot = row.dot) {
        is LogbookDot.Timeline -> TimelineColors.resolve(dot.color, dark)
        is LogbookDot.State -> dot.color.toColor()
        is LogbookDot.Theme -> resolveVariable(dot.variable, dark)
        LogbookDot.Unavailable -> null
    } ?: colors.colorTextSecondary
    Box(modifier, contentAlignment = if (dotCentre == null) Alignment.Center else Alignment.TopCenter) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val x = size.width / 2
            val centre = dotCentre?.toPx() ?: (size.height / 2)
            val gap = RAIL_GAP.toPx()
            val width = RAIL_WIDTH.toPx()
            if (!row.firstOfDay) drawLine(railColor, Offset(x, 0f), Offset(x, centre - gap), width)
            if (!row.lastOfDay) drawLine(railColor, Offset(x, centre + gap), Offset(x, size.height), width)
        }
        val placed = dotCentre?.let { Modifier.offset(y = it - DOT_SIZE / 2) } ?: Modifier
        val dot = placed.size(DOT_SIZE).clip(CircleShape)
        if (row.dot == LogbookDot.Unavailable) {
            Box(dot.border(UNAVAILABLE_BORDER, resolveVariable("disabled-color", dark) ?: railColor, CircleShape))
        } else {
            Box(dot.background(dotColor))
        }
    }
}

/**
 * Who or what caused the row: a user's picture or initials, or an icon, named for accessibility.
 *
 * Upstream: part of `ha-logbook-entry`, `ha-user-badge` (src/panels/logbook/ha-logbook-entry.ts;
 * src/components/user/ha-user-badge.ts)
 */
@Composable
internal fun CauseBadgeView(cause: LogbookCause, badge: CauseBadge) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val modifier = Modifier.size(BADGE_SIZE).semantics { contentDescription = cause.name }
    when (badge) {
        is CauseBadge.Icon -> DashboardIcon(badge.icon, colors.colorTextSecondary, modifier)
        is CauseBadge.User -> Box(
            modifier.clip(CircleShape).background(
                resolveVariable("light-primary-color", dark) ?: colors.colorFillPrimaryQuietResting,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (badge.picture != null) {
                ServerImage(
                    badge.picture,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                )
            } else {
                Text(
                    badge.initials,
                    fontSize = INITIALS_SIZE,
                    color = resolveVariable("text-light-primary-color", dark) ?: colors.colorTextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The day above its first row: "Today · October 11, 2026".
 *
 * Upstream: part of `ha-logbook-renderer` (src/panels/logbook/ha-logbook-renderer.ts)
 */
@Composable
internal fun LogbookDayHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Css.Text.Body.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Medium),
        color = LocalHAColorScheme.current.colorTextPrimary,
        modifier = modifier.padding(top = Css.Space.S4),
    )
}

/**
 * A message in place of the rows: that there are none, or why they couldn't load.
 *
 * Upstream: part of `ha-logbook` (src/panels/logbook/ha-logbook.ts)
 */
@Composable
internal fun LogbookMessage(text: String, color: Color) {
    Text(
        text,
        style = Css.Text.Body,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(Css.Space.S4),
    )
}

private val DOT_SIZE = 10.dp
private val RAIL_WIDTH = 2.dp

/** The rail stops this far from the dot's centre: its radius and a 2dp clearance. */
private val RAIL_GAP = 7.dp
private val UNAVAILABLE_BORDER = 2.dp
private val BADGE_SIZE = 20.dp
private val INITIALS_SIZE = 9.sp
