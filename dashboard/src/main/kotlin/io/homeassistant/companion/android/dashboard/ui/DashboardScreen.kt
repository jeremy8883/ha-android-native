package io.homeassistant.companion.android.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.CardGroup
import io.homeassistant.companion.android.dashboard.model.DashboardInfo
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCard

@Composable
internal fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboards by viewModel.dashboards.collectAsStateWithLifecycle()
    val selectedDashboard by viewModel.selectedDashboardUrlPath.collectAsStateWithLifecycle()
    // Passed down as State so that only the cards whose derived content changes recompose
    val hass = viewModel.hass.collectAsStateWithLifecycle()

    val content = uiState as? DashboardUiState.Content
    BackHandler(enabled = content?.isSubview == true) { viewModel.onBack() }

    DashboardScreenContent(
        uiState = uiState,
        dashboards = dashboards,
        selectedDashboard = selectedDashboard,
        hass = hass,
        onSelectDashboard = viewModel::onSelectDashboard,
        onSelectTab = viewModel::onSelectTab,
        onNavigate = viewModel::onNavigate,
        onBack = { viewModel.onBack() },
    )
}

@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
    dashboards: List<DashboardInfo>,
    selectedDashboard: String?,
    hass: State<HassSnapshot?>,
    onSelectDashboard: (String?) -> Unit,
    onSelectTab: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val content = uiState as? DashboardUiState.Content
    Scaffold(
        topBar = {
            HATopBar(
                title = {
                    Text(
                        when {
                            content?.isSubview == true -> content.subviewTitle.orEmpty()
                            else -> content?.title ?: stringResource(R.string.native_dashboard_title)
                        },
                    )
                },
                onBackClick = if (content?.isSubview == true) onBack else null,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (content?.isSubview != true) DashboardPicker(dashboards, selectedDashboard, onSelectDashboard)
            when (uiState) {
                DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HALoading()
                }
                DashboardUiState.NotFound -> Message(stringResource(R.string.native_dashboard_not_found))
                is DashboardUiState.Error -> Message(
                    stringResource(R.string.native_dashboard_error, uiState.message.orEmpty()),
                )
                is DashboardUiState.UnsupportedStrategy -> Message(
                    stringResource(R.string.native_dashboard_unsupported_strategy, uiState.type.orEmpty()),
                )
                is DashboardUiState.Content -> DashboardView(uiState, hass, onSelectTab, onNavigate)
            }
        }
    }
}

@Composable
private fun DashboardPicker(dashboards: List<DashboardInfo>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.native_dashboard_default)) },
            )
        }
        items(dashboards, key = { it.urlPath.orEmpty() }) { dashboard ->
            FilterChip(
                selected = selected == dashboard.urlPath,
                onClick = { onSelect(dashboard.urlPath) },
                label = { Text(dashboard.title ?: dashboard.urlPath.orEmpty()) },
            )
        }
    }
}

@Composable
private fun DashboardView(
    content: DashboardUiState.Content,
    hass: State<HassSnapshot?>,
    onSelectTab: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column {
        // As upstream, the tab bar only appears with several top-level views
        if (!content.isSubview && content.tabs.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = content.selectedTab) {
                content.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == content.selectedTab,
                        onClick = { onSelectTab(tab.path) },
                        text = { Text(tab.title ?: tab.path) },
                    )
                }
            }
        }
        if (content.groups == null) {
            Message(stringResource(R.string.native_dashboard_unsupported_view))
        } else {
            // A fresh list per view, so each view starts scrolled to the top
            key(content.viewPath) { CardGroups(content.groups, hass, onNavigate) }
        }
    }
}

@Composable
private fun CardGroups(groups: List<CardGroup>, hass: State<HassSnapshot?>, onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        items(groups) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                group.cards.forEach { card -> DashboardCard(card, hass, onNavigate, Modifier.fillMaxWidth()) }
            }
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
            dashboards = listOf(DashboardInfo("dashboard-test", "Test", "storage", requireAdmin = false)),
            selectedDashboard = null,
            hass = mutableStateOf(null),
            onSelectDashboard = {},
            onSelectTab = {},
            onNavigate = {},
            onBack = {},
        )
    }
}
