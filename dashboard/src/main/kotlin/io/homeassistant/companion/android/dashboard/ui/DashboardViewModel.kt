package io.homeassistant.companion.android.dashboard.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.action.resolveAction
import io.homeassistant.companion.android.dashboard.data.DashboardRepository
import io.homeassistant.companion.android.dashboard.data.EnergyRepository
import io.homeassistant.companion.android.dashboard.data.LiveDataRepository
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.ServerActionsRepository
import io.homeassistant.companion.android.dashboard.data.ServerInfo
import io.homeassistant.companion.android.dashboard.data.SidebarData
import io.homeassistant.companion.android.dashboard.data.StoredDashboardConfig
import io.homeassistant.companion.android.dashboard.data.combineLoadables
import io.homeassistant.companion.android.dashboard.data.map
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.derive.TemplateRequest
import io.homeassistant.companion.android.dashboard.derive.TemplateResult
import io.homeassistant.companion.android.dashboard.derive.cameraSnapshotEntities
import io.homeassistant.companion.android.dashboard.derive.templateRequests
import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import io.homeassistant.companion.android.dashboard.energy.EnergyCollection
import io.homeassistant.companion.android.dashboard.energy.energyEnvironment
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.IconResources
import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.withFallback
import io.homeassistant.companion.android.dashboard.layout.CardGroup
import io.homeassistant.companion.android.dashboard.layout.cardGroups
import io.homeassistant.companion.android.dashboard.layout.viewHeaderCard
import io.homeassistant.companion.android.dashboard.layout.withNestedCards
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.DashboardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.navigation.PanelInfo
import io.homeassistant.companion.android.dashboard.navigation.SidebarItem
import io.homeassistant.companion.android.dashboard.navigation.SidebarSettings
import io.homeassistant.companion.android.dashboard.navigation.defaultPanelUrlPath
import io.homeassistant.companion.android.dashboard.navigation.sidebarItems
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import io.homeassistant.companion.android.dashboard.strategy.energy.ENERGY_PANEL
import io.homeassistant.companion.android.dashboard.strategy.energy.energyDashboard
import io.homeassistant.companion.android.dashboard.strategy.expandView
import io.homeassistant.companion.android.dashboard.strategy.home.HomeDashboardConfig
import io.homeassistant.companion.android.dashboard.strategy.home.homeDashboard
import io.homeassistant.companion.android.dashboard.strategy.summary.SUMMARY_PANELS
import io.homeassistant.companion.android.dashboard.strategy.summary.summaryPanelDashboard
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** What the dashboard screen shows. */
sealed interface DashboardUiState {
    /** Nothing loaded yet. */
    data object Loading : DashboardUiState

    /** The dashboard has no stored config and no ported strategy generates one. */
    data object NotFound : DashboardUiState

    /** The dashboard is generated by a strategy that is not ported yet. */
    data class UnsupportedStrategy(val type: String?) : DashboardUiState

    /** Nothing could be loaded to show the dashboard; loading is retried. */
    data class Error(val error: LoadError) : DashboardUiState

    /**
     * @property tabs the top-level views; subviews are only reached through navigation, as upstream
     * @property selectedTab index in [tabs] of the shown view or of the view a subview was opened from
     * @property isSubview whether the shown view is a subview, which shows a back button instead of tabs
     * @property canGoBack whether the dashboard was opened from another page to return to (a `historyBack` link),
     * which shows a back button
     * @property groups the shown view's cards, or `null` when its strategy is not ported yet
     * @property view the shown view with its strategies expanded, for its header and badges
     */
    data class Content(
        val title: String?,
        val tabs: List<ViewTab>,
        val selectedTab: Int,
        val isSubview: Boolean,
        val subviewTitle: String?,
        val canGoBack: Boolean = false,
        val viewPath: String,
        /** The view's `max_columns`, bounding the column count `view_columns` conditions see. */
        val maxColumns: Int?,
        val groups: List<CardGroup>?,
        val view: ViewConfig? = null,
    ) : DashboardUiState {
        /** Whether the top bar shows a back button rather than the menu. */
        val showsBack: Boolean get() = isSubview || canGoBack
    }
}

/**
 * How current the shown data is, apart from the data itself.
 *
 * @property refreshing whether shown data is being loaded again
 * @property offline whether the connection is lost (after a short grace, so a quick reconnection doesn't show)
 * @property updatedAt when the shown data was last current: when the oldest of it was loaded while it comes from
 * before (the cache), otherwise when the connection was lost; `null` while connected and loaded
 * @property refreshError why loading shown data again failed, while the data loaded before stays; connection
 * problems are left to [offline]
 */
data class DashboardStatus(
    val refreshing: Boolean,
    val offline: Boolean,
    val updatedAt: Instant?,
    val refreshError: LoadError?,
) {
    companion object {
        val CURRENT = DashboardStatus(refreshing = false, offline = false, updatedAt = null, refreshError = null)
    }
}

/** A top-level view, selected by [path] (or its index when it has none). */
data class ViewTab(val title: String?, val icon: String?, val path: String)

/**
 * Raw state: the selected dashboard, the stack of opened view paths, the stored config, the registries and server
 * details, and the entity states. The dashboard structure (including generated dashboards) is derived from these.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @VisibleForTesting internal constructor(
    private val repository: DashboardRepository,
    live: LiveDataRepository,
    serverActions: ServerActionsRepository,
    energyRepository: EnergyRepository,
    clock: Clock,
    private val dispatchers: DashboardDispatchers,
) : ViewModel() {

    @Inject
    constructor(
        repository: DashboardRepository,
        live: LiveDataRepository,
        serverActions: ServerActionsRepository,
        energyRepository: EnergyRepository,
        clock: Clock,
    ) : this(
        repository,
        live,
        serverActions,
        energyRepository,
        clock,
        DashboardDispatchers(default = Dispatchers.Default, io = Dispatchers.IO),
    )

    /** `null` is the default dashboard. */
    private val selectedDashboard = MutableStateFlow<String?>(null)

    /** Opened views, last is shown. Empty shows the first view. */
    private val viewStack = MutableStateFlow<List<String>>(emptyList())

    /**
     * Where back returns to after the shown dashboard's first view, last first: the pages that opened a dashboard
     * with a `historyBack` link, such as the overview's summaries opening `/light?historyBack=1`. The frontend goes
     * back in the browser history there.
     */
    private val returnPoints = MutableStateFlow<List<Location>>(emptyList())

    /** The current time for `time` conditions, updated often enough for their minute resolution. */
    val now: StateFlow<ZonedDateTime?> = flow {
        while (true) {
            // The frontend's default time zone setting follows the device
            emit(clock.now().toJavaInstant().atZone(ZoneId.systemDefault()))
            delay(CLOCK_TICK)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val selectedDashboardUrlPath: StateFlow<String?> = selectedDashboard

    private val entityStates = live.entityStates()
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    private val serverInfo = repository.serverInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    // Reading the bundled resource touches disk, so never on the main thread
    private val bundledLocalize = viewModelScope.async(dispatchers.io, start = CoroutineStart.LAZY) {
        // Bundled with the app, so missing only when the build is broken
        checkNotNull(JsonTranslations.bundled(BUNDLED_LANGUAGE)) { "Bundled frontend translations are missing" }
    }

    /** Frontend strings plus the server's entity translations, and the icon translations. */
    private val entityDisplay: Flow<Loadable<Pair<Localize, IconResources>>> = combine(
        flow { emit(bundledLocalize.await()) },
        repository.entityResources(BUNDLED_LANGUAGE),
    ) { bundled, resources -> resources.map { bundled.withFallback(it.translations) to it.icons } }

    /** What upstream strategies fetch while generating, once the loaded integrations are known. */
    private val strategyData: Flow<Loadable<StrategyData>> = serverInfo
        .map { it.valueOrNull?.config?.components }
        .distinctUntilChanged()
        .flatMapLatest { components ->
            if (components == null) flowOf(Loadable.Loading) else repository.strategyData(components)
        }

    /**
     * The data the dashboard structure is derived from. Like upstream, structure is regenerated when registries
     * change, not on state changes, so the states are those at the time of the registry update.
     */
    private val structureInputs: StateFlow<Loadable<StructureInputs>> = combine(
        repository.registries(),
        serverInfo,
        combine(strategyData, repository.homeSystemData(), ::Pair),
        // The states until the first live ones (kept ones show until then); later ones don't change the structure
        entityStates.transformWhile {
            emit(it)
            !(it is Loadable.Ready && it.keptAt == null)
        },
        entityDisplay,
    ) { registries, info, (strategy, home), states, display ->
        combineLoadables(
            combineLoadables(registries, info, strategy, ::ServerData),
            combineLoadables(home, states, display, ::Triple),
        ) { server, (homeSettings, firstStates, entityDisplay) ->
            structureInputs(server, homeSettings, firstStates, entityDisplay)
        }
    }.flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** The navigation sidebar: the panels in the user's order, and which one is the default. */
    val sidebar: StateFlow<Loadable<SidebarState>> = combine(structureInputs, repository.sidebarData()) {
            inputs,
            data,
        ->
        combineLoadables(inputs, data, ::sidebarState)
    }.flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    // Live collections some cards show; only admins may read them, and the cards are hidden for others
    private val isAdmin = serverInfo.map { it.valueOrNull?.user?.isAdmin == true }.distinctUntilChanged()
    private val repairsIssues = isAdmin
        .flatMapLatest { if (it) live.repairsIssues() else flowOf(Loadable.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)
    private val discoveredFlows = isAdmin
        .flatMapLatest { if (it) live.discoveredFlows() else flowOf(Loadable.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    /** The selected dashboard's stored config with the structure inputs, as one. */
    private val dashboard: StateFlow<Pair<String?, Loadable<Pair<StoredDashboardConfig, StructureInputs>>>?> =
        selectedDashboard
            .flatMapLatest { urlPath ->
                // Summary panels and the energy panel are generated, never stored
                val config = if (urlPath in SUMMARY_PANELS || urlPath == ENERGY_PANEL) {
                    flowOf(Loadable.Ready(StoredDashboardConfig.NotStored))
                } else {
                    repository.dashboardConfig(urlPath)
                }
                config.combine(structureInputs) { config, inputs ->
                    urlPath to combineLoadables(config, inputs, ::Pair)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private val otherPages = MutableStateFlow(true)

    /**
     * Whether pages other than the native dashboards can be opened (the host decides); when not, cards that only lead
     * to them are left out.
     */
    var otherPagesAvailable: Boolean
        get() = otherPages.value
        set(value) {
            otherPages.value = value
        }

    val uiState: StateFlow<DashboardUiState> = dashboard
        .filterNotNull()
        .combine(combine(viewStack, otherPages, returnPoints, ::Navigation)) { (urlPath, loadable), navigation ->
            when (loadable) {
                Loadable.Loading -> DashboardUiState.Loading
                is Loadable.Failed -> DashboardUiState.Error(loadable.error)
                is Loadable.Ready -> {
                    val (config, inputs) = loadable.value
                    val state = config.toUiState(urlPath, inputs, navigation.stack)
                        .withReturn(navigation.returnPoints.isNotEmpty())
                    if (navigation.canOpenOtherPages) state else state.withoutLinksOut(inputs.panelInfo)
                }
            }
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DashboardUiState.Loading)

    /** When the connection was lost, after a short grace so a quick reconnection doesn't show. */
    private val offlineSince: Flow<Instant?> = live.connectionStatus()
        .map { it?.state is WebSocketState.Closed }
        .distinctUntilChanged()
        .mapLatest { closed ->
            if (closed) {
                val since = clock.now()
                delay(OFFLINE_GRACE)
                since
            } else {
                null
            }
        }

    /** How current the shown dashboard and states are. */
    val status: StateFlow<DashboardStatus> = combine(
        dashboard.map { it?.second.progress() }.distinctUntilChanged(),
        entityStates.map { it.progress() }.distinctUntilChanged(),
        offlineSince,
    ) { dashboardProgress, statesProgress, offlineSince ->
        val keptAt = listOfNotNull(dashboardProgress.keptAt, statesProgress.keptAt).minOrNull()
        DashboardStatus(
            refreshing = dashboardProgress.refreshing || statesProgress.refreshing,
            offline = offlineSince != null,
            updatedAt = keptAt ?: offlineSince,
            refreshError = (dashboardProgress.refreshError ?: statesProgress.refreshError)
                ?.takeUnless { it == LoadError.NoResponse },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DashboardStatus.CURRENT)

    /**
     * The renderings of the templates the shown view's cards use, by request. Re-subscribed when the view or its
     * requests change, as each card would.
     */
    private val templates: Flow<Map<TemplateRequest, TemplateResult>> = uiState
        .combine(structureInputs.mapNotNull { it.valueOrNull }) { state, inputs ->
            inputs.hass.templateRequests(shownCards(state, withHeader = true))
        }
        .distinctUntilChanged()
        .flatMapLatest { requests ->
            if (requests.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(requests.map { request -> live.renderTemplate(request).map { request to it } }) {
                    it.toMap()
                }.onStart { emit(emptyMap()) }
            }
        }

    /** Signed snapshot paths of the cameras the shown view's cards show, refreshed like the frontend's `hui-image`. */
    private val cameraImages: Flow<Map<String, String>> = uiState
        .combine(structureInputs.mapNotNull { it.valueOrNull }) { state, inputs ->
            inputs.hass.cameraSnapshotEntities(shownCards(state, withHeader = false))
        }
        .distinctUntilChanged()
        .flatMapLatest { cameras -> if (cameras.isEmpty()) flowOf(emptyMap()) else live.cameraSnapshots(cameras) }
        .onStart { emit(emptyMap()) }

    private val energy = EnergyCollections(energyRepository)

    /** The energy collections the shown view's cards read, with their data. */
    private val energyCollections: Flow<Map<String, EnergyCollection>> = energy.collections(
        keys = uiState.map { state ->
            energyCollectionKeys(shownCards(state, withHeader = false), (state as? DashboardUiState.Content)?.view)
        }.distinctUntilChanged(),
        prefs = structureInputs.map { it.valueOrNull?.strategyData?.energyPrefs }.distinctUntilChanged(),
        environment = structureInputs.map { loadable ->
            loadable.valueOrNull?.let { inputs ->
                inputs.strategyData.energyPreferences()?.let(inputs.hass::energyEnvironment)
            }
        }.distinctUntilChanged(),
        now = now,
    ).onStart { emit(emptyMap()) }

    /** The URL server paths (pictures, snapshots) are loaded from. */
    val serverUrl: StateFlow<String?> = live.serverUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** The latest snapshot for cards: structure inputs with live entity states and collections. */
    val hass: StateFlow<HassSnapshot?> = combine(
        structureInputs.mapNotNull { it.valueOrNull },
        entityStates.mapNotNull { it.valueOrNull },
        repairsIssues,
        discoveredFlows,
        combine(templates, cameraImages, energyCollections, ::Triple),
    ) { inputs, states, repairs, flows, (rendered, cameras, energyCollections) ->
        inputs.hass.copy(
            states = states,
            // Not loaded (or not readable) collections stay `null`, never empty
            repairsIssues = repairs.valueOrNull,
            discoveredFlows = flows.valueOrNull,
            templates = rendered,
            cameraImages = cameras,
            energy = energyCollections,
        )
    }.flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** Load again what failed, now rather than at the next retry. */
    fun onRetry() = repository.retry()

    /** Change the period or comparison of the energy collection [collectionKey], as its date selection chose. */
    internal fun onEnergyChange(collectionKey: String, change: EnergyChange) {
        now.value?.let { energy.change(collectionKey, change, it) }
    }

    fun onSelectDashboard(urlPath: String?) {
        returnPoints.value = emptyList()
        viewStack.value = emptyList()
        selectedDashboard.value = urlPath
    }

    fun onSelectTab(path: String) {
        viewStack.value = listOf(path)
    }

    /**
     * Follow a `navigate` action. Paths without a leading slash are views of the current dashboard, as in the
     * frontend; other panels are not available natively yet and are ignored.
     */
    fun onNavigate(navigationPath: String) {
        if (navigationPath.startsWith("/")) return
        val path = navigationPath.substringBefore('?')
        viewStack.update { it + path }
    }

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)

    /** One-off effects for the screen: messages, confirmations, links to open. */
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    private val actions = DashboardActions(
        repository = serverActions,
        events = _events,
        language = BUNDLED_LANGUAGE,
        localize = { hass.value?.localize ?: bundledLocalize.await() },
        navigate = { path -> if (path.startsWith("/")) onOpenPath(path) else onNavigate(path) },
    )

    /**
     * Handle [gesture] on a card element configured by [config] (see `cardActions`): resolve it against the
     * current state and run it, after asking for confirmation when the action wants it.
     */
    fun onGesture(config: JsonObject, gesture: Gesture) {
        val snapshot = hass.value ?: return
        viewModelScope.launch {
            val resolved = withContext(dispatchers.default) { snapshot.resolveAction(config, gesture) } ?: return@launch
            val confirmation = resolved.confirmation
            if (confirmation != null) {
                _events.send(DashboardEvent.Confirm(confirmation, resolved.action))
            } else {
                actions.run(resolved.action)
            }
        }
    }

    /** Open upstream's full more-info dialog for [entityId] in the app's web frontend. */
    fun onShowFullMoreInfo(entityId: String) {
        viewModelScope.launch {
            // The frontend's more-info dialog over the dashboard shown now, back to it natively once closed
            val urlPath = selectedDashboard.value ?: sidebar.loaded(_events)?.defaultPanel ?: return@launch
            val encoded = java.net.URLEncoder.encode(entityId, Charsets.UTF_8)
            _events.send(DashboardEvent.OpenWeb("/$urlPath?$MORE_INFO_PARAM=$encoded"))
        }
    }

    /** Run [action] that a card control started directly (such as a tile feature) or that the user confirmed. */
    fun onAction(action: CardAction) {
        viewModelScope.launch { actions.run(action) }
    }

    /** Run [action] with the [code] the user entered for it. */
    fun onCodeEntered(action: CardAction.CallService, code: String) {
        viewModelScope.launch { actions.runWithCode(action, code) }
    }

    /**
     * Go to an app path such as `/dashboard-test/kitchen` or `/config/devices`: dashboards the native renderer
     * shows open here (with their view), every other panel opens in the web frontend.
     */
    fun onOpenPath(path: String) {
        viewModelScope.launch {
            // The panels decide what is a dashboard, so wait for them
            val state = sidebar.loaded(_events) ?: return@launch
            val segments = path.substringBefore('?').removePrefix("/").split('/').filter { it.isNotEmpty() }
            val urlPath = segments.firstOrNull() ?: state.defaultPanel
            val panel = state.panels[urlPath]
            if (panel == null || !isNativeDashboard(panel)) {
                _events.send(DashboardEvent.OpenWeb(path))
                return@launch
            }
            val here = Location(selectedDashboard.value, viewStack.value)
            returnPoints.update { if (path.hasHistoryBack()) it + here else emptyList() }
            selectedDashboard.value = if (urlPath == HOME_PANEL) null else urlPath
            viewStack.value = segments.drop(1).take(1)
        }
    }

    /**
     * Close the shown view, or from the first view return to the page that opened this dashboard with a
     * `historyBack` link.
     *
     * @return whether back was handled; the first view is implicit, so an empty stack shows it
     */
    fun onBack(): Boolean {
        val back = returnPoints.value.lastOrNull()
        when {
            viewStack.value.isNotEmpty() -> viewStack.update { it.dropLast(1) }
            back != null -> {
                returnPoints.update { it.dropLast(1) }
                selectedDashboard.value = back.dashboard
                viewStack.value = back.stack
            }
            else -> return false
        }
        return true
    }
}

/** The dispatchers derivations run on: [default] for computing, [io] for reading bundled resources. */
data class DashboardDispatchers(val default: CoroutineDispatcher, val io: CoroutineDispatcher)

/** The sidebar once loaded, or `null` (with a message through [events]) when it couldn't be. */
private suspend fun StateFlow<Loadable<SidebarState>>.loaded(events: SendChannel<DashboardEvent>): SidebarState? =
    when (val loaded = first { it != Loadable.Loading }) {
        is Loadable.Ready -> loaded.value
        is Loadable.Failed -> null.also { events.send(DashboardEvent.LoadFailed(loaded.error)) }
        Loadable.Loading -> null
    }

private data class StructureInputs(
    val hass: HassSnapshot,
    val strategyData: StrategyData,
    val homeSettings: JsonObject?,
    val panelInfo: Map<String, PanelInfo>,
)

/** What the structure is derived from that rarely changes. */
private data class ServerData(val registries: Registries, val info: ServerInfo, val strategyData: StrategyData)

private fun structureInputs(
    server: ServerData,
    homeSettings: JsonObject?,
    states: EntityStates,
    display: Pair<Localize, IconResources>,
) = StructureInputs(
    hass = HassSnapshot(
        states = states,
        registries = server.registries,
        user = server.info.user,
        config = server.info.config,
        panels = server.info.panels,
        localize = display.first,
        icons = display.second,
        formats = JdkDisplayFormats(Locale.forLanguageTag(BUNDLED_LANGUAGE), ZoneId.systemDefault()),
    ),
    strategyData = server.strategyData,
    homeSettings = homeSettings,
    panelInfo = server.info.panelInfo,
)

private fun sidebarState(inputs: StructureInputs, data: SidebarData): SidebarState {
    val defaultPanel = defaultPanelUrlPath(data.userCore, data.systemCore, inputs.panelInfo)
    val items = sidebarItems(
        panels = inputs.panelInfo,
        defaultPanel = defaultPanel,
        settings = SidebarSettings.fromUserData(data.sidebar),
        localize = inputs.hass.localize,
        locale = Locale.forLanguageTag(BUNDLED_LANGUAGE),
    )
    return SidebarState(
        items = items,
        dashboardItems = items.filter { item -> inputs.panelInfo[item.urlPath]?.let(::isNativeDashboard) == true },
        panels = inputs.panelInfo,
        defaultPanel = defaultPanel,
        isAdmin = inputs.hass.user?.isAdmin == true,
    )
}

/** The cards of the shown view (with nested ones), and its header card when [withHeader]. */
private fun shownCards(state: DashboardUiState, withHeader: Boolean): List<CardConfig> {
    val content = state as? DashboardUiState.Content ?: return emptyList()
    val cards = withNestedCards(content.groups?.flatMap { it.cards }.orEmpty())
    val header = if (withHeader) listOfNotNull(content.view?.let(::viewHeaderCard)) else emptyList()
    return cards + header
}

/**
 * This, without links to pages other than the native dashboards (see [linksOut]): a heading keeps its title without
 * the link (and its arrow), other cards whose tap only leads there are left out.
 */
private fun DashboardUiState.withoutLinksOut(panels: Map<String, PanelInfo>): DashboardUiState {
    if (this !is DashboardUiState.Content) return this
    return copy(
        groups = groups?.mapNotNull { group ->
            group.copy(cards = group.cards.mapNotNull { it.withoutLinkOut(panels) }).takeIf { it.cards.isNotEmpty() }
        },
    )
}

private fun CardConfig.withoutLinkOut(panels: Map<String, PanelInfo>): CardConfig? = when {
    !linksOut(panels) -> this
    type == HEADING -> CardConfig(JsonObject(json - TAP_ACTION))
    else -> null
}

/**
 * Whether this card's tap navigates to a page that isn't a native dashboard, such as the overview's summaries and its
 * Repairs and Updates cards (`/config/...`).
 */
private fun CardConfig.linksOut(panels: Map<String, PanelInfo>): Boolean {
    val tap = json.obj(TAP_ACTION)
    val path = tap?.string(NAVIGATION_PATH)?.takeIf { tap.string(ACTION) == NAVIGATE && it.startsWith("/") }
    val panel = path?.let { panels[it.removePrefix("/").substringBefore('?').substringBefore('/')] }
    return path != null && (panel == null || !isNativeDashboard(panel))
}

private const val TAP_ACTION = "tap_action"
private const val HEADING = "heading"
private const val ACTION = "action"
private const val NAVIGATE = "navigate"
private const val NAVIGATION_PATH = "navigation_path"

/** Whether data is being loaded again, why it last failed to, and when it was kept, whatever the value. */
private data class Progress(val refreshing: Boolean, val refreshError: LoadError?, val keptAt: Instant?)

private fun Loadable<*>?.progress(): Progress =
    (this as? Loadable.Ready)?.let { Progress(it.refreshing, it.refreshError, it.keptAt) }
        ?: Progress(false, null, null)

private fun StoredDashboardConfig.toUiState(
    urlPath: String?,
    inputs: StructureInputs,
    stack: List<String>,
): DashboardUiState = when (this) {
    // Without a stored default dashboard the frontend shows the generated home dashboard (/home)
    StoredDashboardConfig.NotStored -> when (urlPath) {
        null -> DashboardConfig(inputs.hass.homeDashboard(HomeDashboardConfig.fromSystemData(inputs.homeSettings)))
            .toContent(inputs, stack)
        // Like the frontend's panels, generated from the current registries
        in SUMMARY_PANELS -> DashboardConfig(inputs.hass.summaryPanelDashboard(urlPath)).toContent(inputs, stack)
        ENERGY_PANEL -> DashboardConfig(
            inputs.hass.energyDashboard(inputs.strategyData.energyPreferences(), inputs.strategyData.energyHiddenCards),
        ).toContent(inputs, stack)
        else -> DashboardUiState.NotFound
    }
    is StoredDashboardConfig.Stored -> config.toContent(inputs, stack)
}

private fun DashboardConfig.toContent(inputs: StructureInputs, stack: List<String>): DashboardUiState {
    if (isStrategy) return DashboardUiState.UnsupportedStrategy(strategy?.string("type"))
    val views = views

    // A view is addressed by its path, or by its index when it has none (as frontend URLs do)
    fun pathOf(index: Int) = views[index].path ?: index.toString()
    fun indexOf(path: String) = views.indices.firstOrNull { pathOf(it) == path }

    // Drop paths a config change removed, falling back to the first view
    val shownIndex = stack.lastOrNull()?.let(::indexOf) ?: 0
    val shown = views.getOrNull(shownIndex)
    val tabIndices = views.indices.filter { !views[it].subview }
    val tabIndex = stack.mapNotNull(::indexOf).lastOrNull { it in tabIndices } ?: tabIndices.firstOrNull() ?: 0

    val expanded = shown?.let { inputs.hass.expandView(it, inputs.strategyData) }
    return DashboardUiState.Content(
        title = title,
        tabs = tabIndices.map { ViewTab(views[it].title, views[it].icon, pathOf(it)) },
        selectedTab = tabIndices.indexOf(tabIndex).coerceAtLeast(0),
        isSubview = shown?.subview == true,
        subviewTitle = shown?.title,
        viewPath = if (shown != null) pathOf(shownIndex) else "",
        maxColumns = shown?.json?.number("max_columns")?.toInt(),
        groups = expanded?.let(::cardGroups) ?: if (views.isEmpty()) emptyList() else null,
        view = expanded,
    )
}

private val STOP_TIMEOUT = 5.seconds.inWholeMilliseconds
private val CLOCK_TICK = 15.seconds

/** How long the connection may be lost before the dashboard shows it, as the frontend's disconnect toast waits. */
private val OFFLINE_GRACE = 1.seconds

/** The language of the bundled frontend strings; server translations are fetched in the same language. */
private const val BUNDLED_LANGUAGE = "en"

private const val MORE_INFO_PARAM = "more-info-entity-id"

/** The navigation sidebar's entries and what paths they lead to. */
data class SidebarState(
    val items: List<SidebarItem>,
    /** [items] the native dashboards show themselves, for when other pages can't be opened. */
    val dashboardItems: List<SidebarItem>,
    val panels: Map<String, PanelInfo>,
    val defaultPanel: String,
    val isAdmin: Boolean,
)

/**
 * Whether [panel] is a dashboard the native renderer shows: the home dashboard, Lovelace dashboards, the summary
 * panels (lights, climate, security, maintenance) and the energy panel, which are generated dashboards too.
 */
internal fun isNativeDashboard(panel: PanelInfo): Boolean = panel.componentName == HOME_PANEL ||
    panel.componentName == LOVELACE_PANEL ||
    panel.componentName in SUMMARY_PANELS ||
    panel.componentName == ENERGY_PANEL

private const val HOME_PANEL = "home"
private const val LOVELACE_PANEL = "lovelace"
