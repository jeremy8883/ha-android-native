package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.logbook.CauseBadge
import io.homeassistant.companion.android.dashboard.logbook.LogbookCause
import io.homeassistant.companion.android.dashboard.logbook.LogbookDot
import io.homeassistant.companion.android.dashboard.logbook.LogbookRow
import io.homeassistant.companion.android.dashboard.logbook.causeBadge
import io.homeassistant.companion.android.dashboard.logbook.logbookRows
import io.homeassistant.companion.android.dashboard.logbook.logbookUsers
import io.homeassistant.companion.android.dashboard.logbook.moreInfoPanelPath
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.ServerImage
import io.homeassistant.companion.android.dashboard.ui.charts.TimelineColors
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.LocalDate

/**
 * The logbook section of an entity's details: what happened to it over the last day, newest first, each with what
 * caused it, kept up to date, with a link to the Logbook panel. Port of `ha-more-info-logbook` (frontend@20260624.6
 * src/dialogs/more-info/ha-more-info-logbook.ts) with `ha-logbook`'s narrow rows without names.
 */
@Composable
internal fun MoreInfoLogbook(entityId: String, hass: HassSnapshot, now: Instant, interactions: CardInteractions) {
    val viewModel = hiltViewModel<MoreInfoLogbookViewModel>(key = "logbook-$entityId")
    val request = LogbookRequest(entityId, hass.user?.isAdmin == true)
    LaunchedEffect(request) { viewModel.show(request) }
    val logbook by viewModel.logbook.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        MoreInfoSectionHeader(
            title = hass.localize("$MORE_INFO.logbook"),
            subtitle = null,
            showMore = hass.localize("$MORE_INFO.show_more"),
            onShowMore = {
                val path = moreInfoPanelPath("logbook", entityId, now, hass.formats.zone)
                interactions.onAction(CardAction.Navigate(path, replace = false))
            },
        )
        when (val loaded = logbook) {
            Loadable.Loading -> HALoading(Modifier.align(Alignment.CenterHorizontally))
            is Loadable.Failed -> LogbookMessage(
                "${hass.localize("$LOGBOOK.retrieval_error")}: ${LocalContext.current.loadErrorText(loaded.error)}",
                LocalHAColorScheme.current.colorOnDangerNormal,
            )
            is Loadable.Ready -> {
                val today = LocalDate.ofInstant(now, hass.formats.zone)
                val rows = remember(loaded.value, hass, today) {
                    val (entries, users, traces) = loaded.value
                    hass.logbookRows(entries, hass.logbookUsers(users), traces, now)
                }
                if (rows.isEmpty()) {
                    LogbookMessage(
                        hass.localize("$LOGBOOK.entries_not_found"),
                        LocalHAColorScheme.current.colorTextSecondary,
                    )
                } else {
                    LogbookRows(rows, hass, now, interactions)
                }
            }
        }
    }
}

@Composable
private fun LogbookMessage(text: String, color: Color) {
    Text(
        text,
        style = HATextStyle.BodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
    )
}

/** The rows under their days' headers. A tap on a time shows every time as how long ago it was, or back. */
@Composable
private fun LogbookRows(rows: List<LogbookRow>, hass: HassSnapshot, now: Instant, interactions: CardInteractions) {
    var relative by rememberSaveable { mutableStateOf(false) }
    Column {
        rows.forEach { row ->
            row.dateHeader?.let { header ->
                Text(
                    header,
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Medium),
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    modifier = Modifier.padding(top = HADimens.SPACE4),
                )
            }
            val at = Instant.ofEpochMilli(row.whenMillis)
            LogbookRowContent(
                row = row,
                time = if (relative) hass.formats.relativeTime(at, now) else hass.formats.timeWithSeconds(at),
                badge = row.cause?.let { hass.causeBadge(it) },
                onToggleTime = { relative = !relative },
                onOpenTrace = { path -> interactions.onAction(CardAction.Navigate(path, replace = false)) },
                traceLabel = hass.localize("$LOGBOOK.view_trace"),
            )
        }
    }
}

/** A row: its dot on the day's rail, what happened, and what caused it, its trace and its time at the end. */
@Composable
private fun LogbookRowContent(
    row: LogbookRow,
    time: String,
    badge: CauseBadge?,
    onToggleTime: () -> Unit,
    onOpenTrace: (String) -> Unit,
    traceLabel: String,
) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = ROW_MIN_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        LogbookNode(row, Modifier.width(NODE_WIDTH).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                Text(
                    row.text,
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                row.cause?.let { cause -> badge?.let { CauseBadgeView(cause, it) } }
                row.traceLink?.let { path ->
                    Text(
                        traceLabel,
                        style = HATextStyle.BodyMedium,
                        color = colors.colorOnPrimaryNormal,
                        modifier = Modifier.clickable(role = Role.Button) { onOpenTrace(path) },
                    )
                }
                Text(
                    time,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    modifier = Modifier.widthIn(
                        min = TIME_MIN_WIDTH,
                    ).clickable(role = Role.Button, onClick = onToggleTime),
                )
            }
            if (!row.lastOfDay) HorizontalDivider(color = colors.colorBorderNeutralQuiet)
        }
    }
}

/** The row's dot, with the day's rail through it (from the day's first dot to its last). */
@Composable
private fun LogbookNode(row: LogbookRow, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val railColor = colors.colorBorderNeutralQuiet
    val dotColor = when (val dot = row.dot) {
        is LogbookDot.Timeline -> TimelineColors.resolve(dot.color, dark)
        is LogbookDot.State -> dot.color.toColor()
        is LogbookDot.Theme -> resolveVariable(dot.variable, dark)
        LogbookDot.Unavailable -> null
    } ?: colors.colorTextSecondary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val x = size.width / 2
            val gap = RAIL_GAP.toPx()
            val width = RAIL_WIDTH.toPx()
            if (!row.firstOfDay) drawLine(railColor, Offset(x, 0f), Offset(x, size.height / 2 - gap), width)
            if (!row.lastOfDay) drawLine(railColor, Offset(x, size.height / 2 + gap), Offset(x, size.height), width)
        }
        val dot = Modifier.size(DOT_SIZE).clip(CircleShape)
        if (row.dot == LogbookDot.Unavailable) {
            Box(dot.border(UNAVAILABLE_BORDER, resolveVariable("disabled-color", dark) ?: railColor, CircleShape))
        } else {
            Box(dot.background(dotColor))
        }
    }
}

/** Who or what caused the row: a user's picture or initials, or an icon, named for accessibility. */
@Composable
private fun CauseBadgeView(cause: LogbookCause, badge: CauseBadge) {
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

private const val LOGBOOK = "ui.components.logbook"
private val ROW_MIN_HEIGHT = 40.dp
private val NODE_WIDTH = 28.dp
private val DOT_SIZE = 10.dp
private val RAIL_WIDTH = 2.dp

/** The rail stops this far from the dot's centre: its radius and a 2dp clearance. */
private val RAIL_GAP = 7.dp
private val UNAVAILABLE_BORDER = 2.dp
private val BADGE_SIZE = 20.dp
private val INITIALS_SIZE = 9.sp

/** Times line up: at least 4.5em, as upstream's time chips. */
private val TIME_MIN_WIDTH = 64.dp
