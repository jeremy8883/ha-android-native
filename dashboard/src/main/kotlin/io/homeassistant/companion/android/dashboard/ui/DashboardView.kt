package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.ScreenInfo
import io.homeassistant.companion.android.dashboard.condition.sectionsViewColumns
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.CardGroup
import io.homeassistant.companion.android.dashboard.layout.SECTION_GRID_GAP_DP
import io.homeassistant.companion.android.dashboard.layout.SECTION_ROW_HEIGHT_DP
import io.homeassistant.companion.android.dashboard.layout.SectionLayout
import io.homeassistant.companion.android.dashboard.layout.SidebarLayout
import io.homeassistant.companion.android.dashboard.layout.viewLayout
import io.homeassistant.companion.android.dashboard.layout.viewSidebar
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCard
import io.homeassistant.companion.android.dashboard.ui.cards.LocalConditionContext
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.map

@Composable
internal fun DashboardView(
    content: DashboardUiState.Content,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onSelectTab: (String) -> Unit,
    interactions: CardInteractions,
) {
    val colors = LocalHAColorScheme.current
    Column {
        // As upstream, the tab bar only appears with several top-level views
        if (!content.isSubview && content.tabs.size > 1) {
            PrimaryScrollableTabRow(
                selectedTabIndex = content.selectedTab,
                containerColor = colors.colorSurfaceDefault,
                contentColor = colors.colorOnPrimaryNormal,
                divider = {},
            ) {
                content.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == content.selectedTab,
                        onClick = { onSelectTab(tab.path) },
                        text = { Text(tab.title ?: tab.path) },
                        selectedContentColor = colors.colorOnPrimaryNormal,
                        unselectedContentColor = colors.colorTextSecondary,
                    )
                }
            }
        }
        if (content.groups == null) {
            Message(stringResource(R.string.native_dashboard_unsupported_view))
        } else {
            // A fresh list per view, so each view starts scrolled to the top
            val configuration = LocalConfiguration.current
            val screen = ScreenInfo(configuration.screenWidthDp, configuration.screenHeightDp)
            val maxColumns = sectionsViewColumns(configuration.screenWidthDp, content.maxColumns)
            key(content.viewPath) {
                CardGroups(content.groups, content.view, hass, now, screen, maxColumns, interactions)
            }
        }
    }
}

@Composable
private fun CardGroups(
    groups: List<CardGroup>,
    view: ViewConfig?,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    screen: ScreenInfo,
    maxColumns: Int,
    interactions: CardInteractions,
) {
    val sidebar = remember(view) { view?.let(::viewSidebar) }
    // Re-evaluated on every state, screen or clock change, but only recomposes when the layout changes
    val layout by remember(groups, sidebar, screen, maxColumns) {
        derivedStateOf {
            val context = ConditionContext(maxColumns = maxColumns, screen = screen, now = now.value)
            hass.value?.viewLayout(groups, context, sidebar)
        }
    }
    val viewLayout = layout ?: return
    // One list item per row of sections, so long phone layouts stay lazy
    val rows = remember(viewLayout) { viewLayout.sections.groupBy { it.cell.row }.values.toList() }
    val narrow = screen.widthDp <= NARROW_WIDTH_DP
    val cardContext = remember(screen, maxColumns) { ConditionContext(maxColumns = maxColumns, screen = screen) }
    CompositionLocalProvider(LocalConditionContext provides cardContext) {
        ViewRows(rows, viewLayout.columnCount, viewLayout.sidebar, narrow, view, hass, now, interactions)
    }
}

@Composable
private fun ViewRows(
    rows: List<List<SectionLayout>>,
    columnCount: Int,
    sidebar: SidebarLayout?,
    narrow: Boolean,
    view: ViewConfig?,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val columnGap = if (narrow) HADimens.SPACE2 else HADimens.SPACE8
    var showSidebar by rememberSaveable(view) { mutableStateOf(false) }
    val sectionRow: @Composable (List<SectionLayout>) -> Unit = { sections ->
        DashboardGrid(
            cells = sections.map { it.cell.copy(row = 0, rowSpan = 1) },
            columnCount = columnCount,
            columnGap = columnGap,
            rowGap = HADimens.SPACE6,
        ) { index -> SectionGrid(sections[index], hass, now, interactions) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the end to scroll the last cards clear of the offline bar, kept so nothing moves when it shows
        contentPadding = PaddingValues(
            start = HADimens.SPACE4,
            top = HADimens.SPACE4,
            end = HADimens.SPACE4,
            bottom = BOTTOM_BAR_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        if (view != null) item { ViewHeader(view, hass, now, interactions) }
        when (sidebar?.mode) {
            // Content and sidebar side by side, scrolling together like upstream's grid
            SidebarLayout.MODE_COLUMN -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                    Column(
                        modifier = Modifier.weight(columnCount.toFloat()),
                        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
                    ) { rows.forEach { sectionRow(it) } }
                    SidebarSections(sidebar, hass, now, interactions, Modifier.weight(1f))
                }
            }
            SidebarLayout.MODE_TABS -> {
                item { SidebarSwitch(sidebar, showSidebar) { showSidebar = it } }
                if (showSidebar) {
                    item { SidebarSections(sidebar, hass, now, interactions, Modifier.fillMaxWidth()) }
                } else {
                    items(rows) { sectionRow(it) }
                }
            }
            else -> items(rows) { sectionRow(it) }
        }
    }
}

@Composable
private fun SidebarSections(
    sidebar: SidebarLayout,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
        sidebar.sections.forEach { SectionGrid(it, hass, now, interactions) }
    }
}

/** The switch between the content and the sidebar on narrow screens (upstream's `mobile-tabs`). */
@Composable
private fun SidebarSwitch(sidebar: SidebarLayout, showSidebar: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        listOf(false to sidebar.contentLabel, true to sidebar.sidebarLabel).forEach { (isSidebar, label) ->
            FilterChip(
                selected = showSidebar == isSidebar,
                onClick = { onChange(isSidebar) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.colorFillPrimaryLoudResting,
                    selectedLabelColor = colors.colorOnPrimaryLoud,
                ),
                modifier = Modifier.padding(horizontal = HADimens.SPACE1),
            )
        }
    }
}

@Composable
private fun SectionGrid(
    section: SectionLayout,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    DashboardGrid(
        cells = section.cards.map { it.cell },
        columnCount = section.columnCount,
        columnGap = HADimens.SPACE2,
        rowGap = HADimens.SPACE2,
    ) { index ->
        section.cards[index].let { placed ->
            // Fixed-row cards keep the web's row rhythm but may grow to fit native content
            val rows = placed.fixedRows
            val sizing = if (rows != null) {
                Modifier.heightIn(min = SECTION_ROW_HEIGHT_DP.dp * rows + SECTION_GRID_GAP_DP.dp * (rows - 1))
            } else {
                Modifier
            }
            DashboardCard(placed.card, hass, now, interactions, sizing.fillMaxWidth())
        }
    }
}

/** Screens up to this width use the narrow gaps between sections, like upstream's 600px breakpoint. */
private const val NARROW_WIDTH_DP = 600

/** About the offline bar's height with its margins. */
private val BOTTOM_BAR_CLEARANCE = 80.dp

/** The [view]'s footer card (`footer.card`), if it has one: the energy views' date selection. */
@Composable
internal fun ViewFooter(
    view: ViewConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val card = remember(view) { view.json.obj("footer")?.obj("card")?.let(::CardConfig) } ?: return
    DashboardCard(
        card = card,
        hass = hass,
        now = now,
        interactions = interactions,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(HADimens.SPACE2),
    )
}
