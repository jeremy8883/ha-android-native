package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.theme.Css

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
        contentPadding = PaddingValues(Css.Space.S6),
        verticalArrangement = Arrangement.spacedBy(Css.Space.S4),
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
            style = Css.Text.Body.copy(textAlign = TextAlign.Start, fontSize = SECTION_TITLE_SIZE),
            fontWeight = FontWeight.Medium,
            color = colors.colorTextPrimary,
            modifier = Modifier.padding(bottom = Css.Space.S2),
        )
        // UPSTREAM-DIFF: the app's grey filled card, where upstream's `ha-card` here is outlined
        DashboardCardSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(horizontal = Css.Space.S4, vertical = Css.Space.S2)) {
                if (entries.isEmpty() && empty != null) {
                    Text(
                        empty,
                        style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                        color = colors.colorTextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Css.Space.S2),
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
            Text(
                name,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
            Text(value, style = Css.Text.Body.copy(textAlign = TextAlign.End), color = colors.colorTextPrimary)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = Css.Space.S2),
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

/** `.data-entry .value { max-width: 60% }`. */
private const val VALUE_MAX_WIDTH = 0.6f

/** The sections' titles, `--ha-font-size-m`. */
private val SECTION_TITLE_SIZE = Css.FontSize.M
