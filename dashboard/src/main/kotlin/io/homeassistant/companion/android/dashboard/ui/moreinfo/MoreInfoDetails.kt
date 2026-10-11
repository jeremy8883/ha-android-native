package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.moreInfoDetailsYaml
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

// Port of `ha-more-info-details` (frontend@20260624.6 src/dialogs/more-info/ha-more-info-details.ts): the details
// view, as cards of entries or as YAML.

/**
 * Port of `ha-more-info-details`: the state's entries, then the attributes ("None" without any), each section a card
 * under its title.
 *
 * Upstream: `ha-more-info-details` (src/dialogs/more-info/ha-more-info-details.ts)
 */
@Composable
internal fun ColumnScope.MoreInfoDetailsView(model: MoreInfoModel, localize: Localize) {
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(HADimens.SPACE6),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        item(key = "state") {
            DetailsSection(localize("ui.components.entity.entity-state-picker.state"), model.details, empty = null)
        }
        item(key = "attributes") {
            DetailsSection(
                localize("ui.dialogs.more_info_control.attributes"),
                model.detailAttributes,
                empty = localize("ui.common.none"),
            )
        }
    }
}

/**
 * A section of the details: its [title], and a card with its [entries] (names and values, divided), or [empty].
 *
 * Upstream: part of `ha-more-info-details` (src/dialogs/more-info/ha-more-info-details.ts)
 */
@Composable
private fun DetailsSection(title: String, entries: List<Pair<String, String>>, empty: String?) {
    val colors = LocalHAColorScheme.current
    Column {
        Text(
            title,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start, fontSize = SECTION_TITLE_SIZE),
            fontWeight = FontWeight.Medium,
            color = colors.colorTextPrimary,
            modifier = Modifier.padding(bottom = HADimens.SPACE2),
        )
        DashboardCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2)) {
                if (entries.isEmpty() && empty != null) {
                    Text(
                        empty,
                        style = HATextStyle.Body,
                        color = colors.colorTextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE2),
                    )
                }
                entries.forEachIndexed { index, (name, value) ->
                    if (index > 0) HAHorizontalDivider()
                    DetailsEntry(name, value)
                }
            }
        }
    }
}

/**
 * An entry: the name on the left in the secondary colour, the value on the right, at most 60% of the width.
 *
 * Upstream: part of `ha-more-info-details` (src/dialogs/more-info/ha-more-info-details.ts)
 */
@Composable
private fun DetailsEntry(name: String, value: String) {
    val colors = LocalHAColorScheme.current
    Layout(
        content = {
            Text(name, style = HATextStyle.Body.copy(textAlign = TextAlign.Start), color = colors.colorTextSecondary)
            Text(value, style = HATextStyle.Body.copy(textAlign = TextAlign.End), color = colors.colorTextPrimary)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE2),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val valuePlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, maxWidth = (width * VALUE_MAX_WIDTH).toInt()),
        )
        val namePlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = (width - valuePlaceable.width).coerceAtLeast(0)),
        )
        val height = maxOf(namePlaceable.height, valuePlaceable.height)
        layout(width, height) {
            namePlaceable.placeRelative(0, 0)
            valuePlaceable.placeRelative(width - valuePlaceable.width, 0)
        }
    }
}

/**
 * Port of the details' YAML mode: the state and attributes as `ha-yaml-editor` shows them read-only, with line
 * numbers, selectable to copy.
 *
 * Upstream: part of `ha-more-info-details`, `ha-yaml-editor`, `ha-code-editor`
 * (src/dialogs/more-info/ha-more-info-details.ts; src/components/ha-yaml-editor.ts;
 * src/components/ha-code-editor.ts)
 */
@Composable
internal fun ColumnScope.MoreInfoDetailsYamlView(entityId: String, hass: HassSnapshot) {
    val yaml = remember(hass.states[entityId], hass.localize) { hass.moreInfoDetailsYaml(entityId) } ?: return
    // The final line break leaves an empty last line, as the code editor shows it
    val lines = remember(yaml) { yaml.split('\n') }
    val dark = isSystemInDarkTheme()
    val colors = LocalHAColorScheme.current
    val background = resolveVariable(CODE_BACKGROUND, dark) ?: colors.colorSurfaceLow
    val gutter = resolveVariable(GUTTER_BACKGROUND, dark) ?: Color.Transparent
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = CODE_FONT_SIZE, lineHeight = CODE_LINE_HEIGHT)
    Box(Modifier.fillMaxWidth().weight(1f).padding(HADimens.SPACE6)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background, RoundedCornerShape(HARadius.L))
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.background(gutter).padding(horizontal = HADimens.SPACE2),
                horizontalAlignment = Alignment.End,
            ) {
                // One text like the code's, so the numbers keep to its lines
                Text(
                    lines.indices.joinToString("\n") { "${it + 1}" },
                    style = code.copy(textAlign = TextAlign.End),
                    color = colors.colorTextSecondary,
                    modifier = Modifier.widthIn(min = GUTTER_MIN_WIDTH),
                )
            }
            SelectionContainer(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(
                    lines.joinToString("\n"),
                    style = code,
                    color = colors.colorTextPrimary,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = HADimens.SPACE2),
                )
            }
        }
    }
}

/** `.data-entry .value { max-width: 60% }`. */
private const val VALUE_MAX_WIDTH = 0.6f
private const val CODE_BACKGROUND = "card-background-color"
private const val GUTTER_BACKGROUND = "secondary-background-color"

/** The sections' titles, `--ha-font-size-m`. */
private val SECTION_TITLE_SIZE = 14.sp
private val CODE_FONT_SIZE = 14.sp
private val CODE_LINE_HEIGHT = 20.sp
private val GUTTER_MIN_WIDTH = HADimens.SPACE5
