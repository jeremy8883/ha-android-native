package io.homeassistant.companion.android.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.NativeDashboardRequest
import io.homeassistant.companion.android.dashboard.NativeDashboardWeb
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.ScreenInfo
import io.homeassistant.companion.android.dashboard.condition.sectionsViewColumns
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.CardGroup
import io.homeassistant.companion.android.dashboard.layout.SECTION_GRID_GAP_DP
import io.homeassistant.companion.android.dashboard.layout.SECTION_ROW_HEIGHT_DP
import io.homeassistant.companion.android.dashboard.layout.SectionLayout
import io.homeassistant.companion.android.dashboard.layout.SidebarLayout
import io.homeassistant.companion.android.dashboard.layout.viewLayout
import io.homeassistant.companion.android.dashboard.layout.viewSidebar
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCard
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.cards.LocalServerUrl
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenWeb: ((String) -> Unit)? = null,
    request: NativeDashboardRequest = NativeDashboardRequest(),
    onSwitchServer: (() -> Unit)? = null,
    web: NativeDashboardWeb? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDashboard by viewModel.selectedDashboardUrlPath.collectAsStateWithLifecycle()
    // Passed down as State so that only the cards whose derived content changes recompose
    val hass = viewModel.hass.collectAsStateWithLifecycle()
    val now = viewModel.now.collectAsStateWithLifecycle()

    val status by viewModel.status.collectAsStateWithLifecycle()
    val content = uiState as? DashboardUiState.Content
    val webVisible = web?.visible == true
    BackHandler(enabled = !webVisible && content?.isSubview == true) { viewModel.onBack() }
    // Like other top-level destinations, back from another dashboard returns to the default one before leaving
    BackHandler(enabled = !webVisible && content?.isSubview != true && selectedDashboard != null) {
        viewModel.onSelectDashboard(null)
    }
    val snackbar = remember { SnackbarHostState() }
    RefreshErrorMessages(
        refreshErrors = remember(viewModel) {
            viewModel.status.map { it.refreshError.takeIf { _ -> it.offlineSince == null } }.distinctUntilChanged()
        },
        snackbar = snackbar,
        onRetry = viewModel::onRetry,
    )
    val interactions = remember(viewModel) { CardInteractions(viewModel::onGesture, viewModel::onAction) }
    var moreInfo by rememberSaveable { mutableStateOf<String?>(null) }
    DashboardEffects(
        events = viewModel.events,
        snackbar = snackbar,
        onConfirmed = viewModel::onConfirmed,
        onCodeEntered = viewModel::onCodeEntered,
        onMoreInfo = { moreInfo = it },
        onOpenWeb = onOpenWeb ?: viewModel::onOpenWebViaDeepLink,
        onShowDashboard = { web?.onShowDashboard?.invoke() },
    )
    val sidebar by viewModel.sidebar.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Saved, so the request isn't applied again when the screen is recreated
    var appliedRequest by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(request) {
        if (appliedRequest == request.id) return@LaunchedEffect
        appliedRequest = request.id
        request.path?.let(viewModel::onOpenPath)
        request.moreInfoEntityId?.let { moreInfo = it }
        if (request.openDrawer) drawerState.open()
    }
    val scope = rememberCoroutineScope()

    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalServerUrl provides serverUrl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // The frontend has its own gestures, so over it the drawer only opens from its menu button
            gesturesEnabled = drawerState.isOpen || (!webVisible && content?.isSubview != true),
            drawerContent = {
                NavigationDrawerContent(
                    sidebar = sidebar,
                    selected = if (webVisible) web?.panel else selectedDashboard ?: sidebar.valueOrNull?.defaultPanel,
                    onRetry = viewModel::onRetry,
                    onSwitchServer = onSwitchServer?.let { switch ->
                        {
                            scope.launch { drawerState.close() }
                            switch()
                        }
                    },
                ) { path ->
                    scope.launch { drawerState.close() }
                    viewModel.onOpenPath(path)
                }
            },
        ) {
            // Back presses go to the drawer while it is open
            val drawerClosed = drawerState.isClosed
            ScreenLayer(visible = !webVisible, backEnabled = drawerClosed) {
                DashboardScreenContent(
                    uiState = uiState,
                    hass = hass,
                    now = now,
                    onSelectTab = viewModel::onSelectTab,
                    interactions = interactions,
                    onBack = { viewModel.onBack() },
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    snackbar = snackbar,
                    status = status,
                    onRetry = viewModel::onRetry,
                )
            }
            web?.let { ScreenLayer(visible = it.visible, backEnabled = drawerClosed, content = it.content) }
        }
        moreInfo?.let { entityId ->
            MoreInfoSheet(
                entityId = entityId,
                hass = hass,
                now = now,
                interactions = interactions,
                onShowFull = {
                    moreInfo = null
                    viewModel.onShowFullMoreInfo(entityId)
                },
                onDismiss = { moreInfo = null },
            )
        }
    }
}

@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onSelectTab: (String) -> Unit,
    interactions: CardInteractions,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    status: DashboardStatus = DashboardStatus.CURRENT,
    onRetry: () -> Unit = {},
) {
    val content = uiState as? DashboardUiState.Content
    Scaffold(
        // Over the content, so they never move it
        snackbarHost = { DashboardBottomBars(snackbar, status.offlineSince, content != null, now) },
        topBar = {
            val title = @Composable {
                Text(
                    when {
                        content?.isSubview == true -> content.subviewTitle.orEmpty()
                        else -> content?.title ?: stringResource(R.string.native_dashboard_title)
                    },
                )
            }
            if (content?.isSubview == true) {
                HATopBar(title = title, onBackClick = onBack)
            } else {
                HATopBar(
                    title = title,
                    navigationIcon = {
                        val menuLabel = stringResource(R.string.native_dashboard_menu)
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier.semantics {
                                contentDescription = menuLabel
                            },
                        ) {
                            DashboardIcon(name = MENU_ICON, tint = LocalHAColorScheme.current.colorTextPrimary)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HALoading()
                }
                DashboardUiState.NotFound -> Message(stringResource(R.string.native_dashboard_not_found))
                is DashboardUiState.Error -> LoadErrorScreen(uiState.error, onRetry)
                is DashboardUiState.UnsupportedStrategy -> Message(
                    stringResource(R.string.native_dashboard_unsupported_strategy, uiState.type.orEmpty()),
                )
                is DashboardUiState.Content -> DashboardView(uiState, hass, now, onSelectTab, interactions)
            }
            RefreshIndicator(visible = status.refreshing, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun DashboardView(
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

@Composable
private fun Message(text: String) {
    Text(text, style = HATextStyle.Body, modifier = Modifier.padding(HADimens.SPACE4))
}

@Preview
@Composable
private fun DashboardScreenNotFoundPreview() {
    HAThemeForPreview {
        DashboardScreenContent(
            uiState = DashboardUiState.NotFound,
            hass = mutableStateOf(null),
            now = mutableStateOf(null),
            onSelectTab = {},
            interactions = CardInteractions.NONE,
            onBack = {},
        )
    }
}

/** Screens up to this width use the narrow gaps between sections, like upstream's 600px breakpoint. */
private const val NARROW_WIDTH_DP = 600

private const val MENU_ICON = "mdi:menu"

/** About the offline bar's height with its margins. */
private val BOTTOM_BAR_CLEARANCE = 80.dp
