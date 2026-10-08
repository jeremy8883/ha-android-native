package io.homeassistant.companion.android.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.DashboardConfigResult
import io.homeassistant.companion.android.dashboard.data.DashboardRepository
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.layout.CardGroup
import io.homeassistant.companion.android.dashboard.layout.cardGroups
import io.homeassistant.companion.android.dashboard.model.DashboardInfo
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What the dashboard screen shows. */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /** The dashboard has no stored config; generating one (strategies) is not supported yet. */
    data object NotFound : DashboardUiState

    data class Error(val message: String?) : DashboardUiState

    data class Content(
        val title: String?,
        val viewTitles: List<String?>,
        val selectedView: Int,
        val groups: List<CardGroup>,
    ) : DashboardUiState
}

/**
 * Raw state is the selected dashboard and view, the dashboard config and the entity states; the rest is
 * derived. Entity states are exposed separately so each card derives only what it shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: DashboardRepository) : ViewModel() {

    /** `null` is the default dashboard. */
    private val selectedDashboard = MutableStateFlow<String?>(null)
    private val selectedView = MutableStateFlow(0)

    val dashboards: StateFlow<List<DashboardInfo>> = flow { emit(repository.dashboards()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val selectedDashboardUrlPath: StateFlow<String?> = selectedDashboard

    val uiState: StateFlow<DashboardUiState> = selectedDashboard
        .flatMapLatest { repository.dashboardConfig(it) }
        .combine(selectedView) { result, viewIndex -> result.toUiState(viewIndex) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DashboardUiState.Loading)

    val entityStates: StateFlow<EntityStates> = repository.entityStates()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyMap())

    fun onSelectDashboard(urlPath: String?) {
        selectedView.value = 0
        selectedDashboard.value = urlPath
    }

    fun onSelectView(index: Int) {
        selectedView.value = index
    }
}

private fun DashboardConfigResult.toUiState(viewIndex: Int): DashboardUiState = when (this) {
    DashboardConfigResult.NotFound -> DashboardUiState.NotFound
    is DashboardConfigResult.Error -> DashboardUiState.Error(message)
    is DashboardConfigResult.Loaded -> {
        val views = config.views
        // A config change can remove views, so clamp instead of keeping a stale index
        val index = viewIndex.coerceIn(0, (views.size - 1).coerceAtLeast(0))
        DashboardUiState.Content(
            title = config.title,
            viewTitles = views.map { it.title ?: it.path },
            selectedView = index,
            groups = views.getOrNull(index)?.let(::cardGroups).orEmpty(),
        )
    }
}

private val STOP_TIMEOUT = 5.seconds.inWholeMilliseconds
