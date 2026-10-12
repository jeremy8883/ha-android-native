package io.homeassistant.companion.android.dashboard.ui.panels.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.logbook.CausePhrase
import io.homeassistant.companion.android.dashboard.logbook.LogbookRow
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * What a named row shows beyond the row: its [time], what caused it ([cause]) and the label of its trace's link.
 */
internal data class LogbookListTexts(val time: String, val cause: CausePhrase?, val traceLabel: String)

/** What a named row's taps do: open an entity's details, a run's trace, or show every time the other way. */
internal data class LogbookListActions(
    val onOpenEntity: (String) -> Unit,
    val onOpenTrace: (String) -> Unit,
    val onToggleTime: () -> Unit,
)

/**
 * A row of the Logbook panel (`ha-logbook-entry`'s "list" layout with `show-cause`): its dot on the day's rail; what
 * it is about and what happened; where that is and when; then what caused it, with its trace.
 *
 * Upstream: `ha-logbook-entry` (src/panels/logbook/ha-logbook-entry.ts)
 */
@Composable
internal fun LogbookListRow(row: LogbookRow, texts: LogbookListTexts, actions: LogbookListActions) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = ROW_MIN_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
    ) {
        LogbookNode(row, Modifier.width(NODE_WIDTH).fillMaxHeight(), dotCentre = DOT_CENTRE)
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.padding(vertical = Css.Space.S2),
                verticalArrangement = Arrangement.spacedBy(Css.Space.S1),
            ) {
                PrimaryLine(row, actions.onOpenEntity)
                SecondaryLine(row.subject?.context, texts.time, actions.onToggleTime)
                if (texts.cause != null || row.traceLink != null) {
                    CauseLine(texts.cause, row.traceLink, texts.traceLabel, actions)
                }
            }
            if (!row.lastOfDay) HorizontalDivider(color = colors.colorBorderNeutralQuiet)
        }
    }
}

/**
 * What the row is about (opening its details while it exists) and what happened, which is never cut.
 *
 * Upstream: part of `ha-logbook-entry` (src/panels/logbook/ha-logbook-entry.ts)
 */
@Composable
private fun PrimaryLine(row: LogbookRow, onOpenEntity: (String) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        val entityId = row.subject?.entityId
        Text(
            row.subject?.name.orEmpty(),
            style = Css.Text.Body.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Medium),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(
                if (entityId != null) Modifier.clickable(role = Role.Button) { onOpenEntity(entityId) } else Modifier,
            ),
        )
        Text(
            row.text,
            style = Css.Text.Body.copy(textAlign = TextAlign.End),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
        )
    }
}

/**
 * Where the entity is, and when it happened (a tap shows every time as how long ago it was, or back).
 *
 * Upstream: part of `ha-logbook-entry` (src/panels/logbook/ha-logbook-entry.ts)
 */
@Composable
private fun SecondaryLine(context: String?, time: String, onToggleTime: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Css.Space.S2)) {
        // `.secondary`: `--ha-font-size-s`
        Text(
            context.orEmpty(),
            style = secondaryStyle,
            color = colors.colorTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // `.time-chip`: `--ha-font-size-s` at `line-height: 1`
        Text(
            time,
            style = secondaryStyle.copy(textAlign = TextAlign.End, lineHeight = TIME_LINE_HEIGHT.em),
            color = colors.colorTextSecondary,
            maxLines = 1,
            modifier = Modifier.widthIn(min = TIME_MIN_WIDTH).clickable(role = Role.Button, onClick = onToggleTime),
        )
    }
}

/**
 * What caused it ("By automation: " and the automation, opening its details), then the run's trace.
 *
 * Upstream: part of `ha-logbook-entry` (src/panels/logbook/ha-logbook-entry.ts)
 */
@Composable
private fun CauseLine(cause: CausePhrase?, traceLink: String?, traceLabel: String, actions: LogbookListActions) {
    val colors = LocalHAColorScheme.current
    val style = secondaryStyle
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Css.Space.S1)) {
        cause?.let { phrase ->
            if (phrase.text.isNotBlank()) Text(phrase.text.trimEnd(), style = style, color = colors.colorTextSecondary)
            phrase.link?.let { link ->
                Text(
                    link.name,
                    style = style,
                    color = colors.colorOnPrimaryNormal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable(role = Role.Button) {
                        actions.onOpenEntity(link.entityId)
                    },
                )
            }
        }
        traceLink?.let { path ->
            Text(
                traceLabel,
                style = style,
                color = colors.colorOnPrimaryNormal,
                modifier = Modifier.padding(start = Css.Space.S1).clickable(role = Role.Button) {
                    actions.onOpenTrace(path)
                },
            )
        }
    }
}

private val ROW_MIN_HEIGHT = 60.dp
private val NODE_WIDTH = 28.dp

/** The dot beside the first line: the content's top padding and half its line. */
private val DOT_CENTRE = 20.dp

/** What happened is never cut, up to this. */
private val VALUE_MAX_WIDTH = 200.dp

/** Times line up: at least 6em on a 12-hour clock, as upstream's time chips. */
private val TIME_MIN_WIDTH = 84.dp

/** `.time-chip`'s `line-height: 1`. */
private const val TIME_LINE_HEIGHT = 1f

/** `.secondary`: `--ha-font-size-s`, at the entry's `--ha-line-height-normal`. */
private val secondaryStyle: TextStyle
    @Composable
    get() = Css.Text.Body.copy(
        fontSize = Css.FontSize.S,
        lineHeight = Css.LineHeight.Normal,
        textAlign = TextAlign.Start,
    )
