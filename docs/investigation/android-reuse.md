# Native Lovelace renderer: what the Android app gives us to reuse

Repo: `home-assistant/android` main @ `eaa2122730ace117bbe7181361b2a86f12be9aac`.
Goal: a native Jetpack Compose (Material 3) Lovelace dashboard renderer as an alternative to the
WebView frontend, kept as a fork that regularly merges upstream `main`.

All paths are relative to the repo root. `…/android/` = `src/main/kotlin/io/homeassistant/companion/android/`.
Everything below was checked against the source at this commit.

---

## 1. Modules, flavors, toolchain

### Modules (`settings.gradle.kts`)
`:common`, `:app`, `:wear`, `:automotive`, `:testing-unit`, `:lint`, `:microwakeword`,
`:provides-sensor-processor`, plus `includeBuild("build-logic")` (convention plugins).

| Module | Contents |
|---|---|
| `:common` (Android library, namespace `io.homeassistant.companion.android.common`) | The whole data layer: `ServerManager`, auth, REST (Retrofit), WebSocket (OkHttp), Room DB (`database/`), `Entity` model plus display helpers, `HATheme` and `HA*` composables (`common/compose/`), **all strings** (`common/src/main/res/values/strings.xml`), sensors. |
| `:app` | Mobile UI: `frontend/` (WebView screen plus external bus), `launch/`, `onboarding/`, `settings/` (mostly legacy PreferenceFragment), `widgets/` (Glance plus legacy RemoteViews), `controls/` (Device Controls), `qs/`, `vehicle/`, `util/compose/` (`HAApp`, `HANavHost`, `HAWebView`, `EntityPicker`, ...). |
| `:automotive` | Separate application that **compiles `:app` sources** (`automotive/build.gradle.kts` adds `../app/src/main/kotlin` to its source sets). Anything `:app/main` references must also resolve for `:automotive`. |
| `:wear` | Wear OS app (Wear Compose M3). Has its own native entity list. |
| `:testing-unit` | Test helpers (no dependency on `:common`). |

### Convention plugins (`build-logic/convention/src/main/kotlin/`)
- `AndroidCommonConventionPlugin`: applies kotlinx-serialization, KSP and Hilt to **every** module.
  Sets compileSdk/minSdk, Java 11 bytecode (`javaVersion = "11"`), core library desugaring, JUnit
  Platform, Robolectric `--add-opens`, lint `warningsAsErrors = true` with `lint-baseline.xml`. Adds
  test deps: JUnit5 + vintage, MockK, Robolectric, Turbine, hilt-testing, coroutines-test, `:testing-unit`.
- `AndroidComposeConventionPlugin`: compose compiler, compose BOM, `material3`, `ui-tooling`,
  `lifecycle-runtime-compose`, and the **screenshot test plugin** (`com.android.compose.screenshot`).
- `AndroidApplicationConventionPlugin`: always applies `com.google.gms.google-services` (see §10),
  debug `applicationIdSuffix = ".debug"`.
- `AndroidFullMinimalFlavorConventionPlugin`: flavor dimension `version`. `minimal` gets
  `applicationIdSuffix ".minimal"`, `full` gets no suffix.
- `AndroidApplicationDependenciesConventionPlugin`: **shared dependency list for `:app` and
  `:automotive`**, including `implementation(project(":common"))`. Full-only: Play Services
  location/home/thread/wearable, Firebase messaging, Sentry. Minimal: embedded Cronet.

### Versions (`gradle/libs.versions.toml`)
| Item | Value |
|---|---|
| minSdk / target / compile | `androidSdk-min = 23`, target 37, compile 37 (automotive min 29, wear min 26) |
| Kotlin | 2.4.20 (K2), KSP |
| Compose BOM | `2026.08.00`; `compose-material3` (M3) plus legacy `compose-material` (M2) still on the app classpath |
| Navigation Compose | 2.9.8 (type-safe `@Serializable` routes), `androidx-hilt-navigation-compose` |
| Hilt | 2.60.1 |
| Room | **Room 3** `androidx.room3:*` 3.0.3 (package `androidx.room3`, not `androidx.room`) |
| Serialization | kotlinx.serialization 1.11.0. **No Jackson anywhere.** Shared mapper `kotlinJsonMapper` in `common/…/common/util/JsonUtil.kt` (snake_case naming strategy, `ignoreUnknownKeys`), plus `MapAnySerializer` for `Map<String, Any?>` |
| Networking | OkHttp 5.5.0, Retrofit 3.0.0 + kotlinx converter |
| Images | Coil 3 (`coil-okhttp`, `coil-svg`); singleton loader set in `HomeAssistantApplication.initializeCoil` on the HA OkHttp client |
| Icons | `io.github.timoptr:mdi-icons` 0.2.0 (`api` from `:common`; `MdiIcon`, `Mdi.fromHaName("mdi:…")` in `common/…/common/util/MdiIcons.kt`) |
| Tests | Robolectric 4.17 (SDK 37), Turbine 1.2.1, screenshot plugin `0.0.1-alpha15` |
| Gradle | 9.8.0 wrapper; CI uses **JDK 21** (Temurin) |

**Dependency locking is global.** Root `build.gradle.kts` has `allprojects { dependencyLocking { lockAllConfigurations() } }`,
and every module has its own `gradle.lockfile` (the app one is about 514 KB). Adding a dependency
means running `./gradlew alldependencies --write-locks`. Lockfiles are a real merge-conflict
hotspot because Renovate bumps them all the time (see §9).

---

## 2. Servers and auth

### `ServerManager` (`common/…/common/data/servers/ServerManager.kt`, impl `ServerManagerImpl`)
- Hilt singleton interface. You can inject it anywhere.
- Supports multiple servers. `SERVER_ID_ACTIVE = -1` is the sentinel used by every API.
  - `getServer(id = SERVER_ID_ACTIVE): Server?`, `servers()`, `serversFlow: Flow<List<Server>>`, `isRegistered()`, `activateServer(id)`.
  - Per-server repositories, created lazily and cached in a mutex-guarded `ServerMap`:
    `authenticationRepository(id)`, `integrationRepository(id)`, `webSocketRepository(id)`,
    `connectionStateProvider(id)`. These throw `IllegalStateException` for an unknown id. Null-returning variants:
    `ServerManager.integrationRepositoryOrNull()` / `webSocketRepositoryOrNull()`.
- `Server` is a Room entity (`common/…/database/server/Server.kt`) with embedded
  `ServerConnectionInfo` (internal/external URL, webhook id, SSIDs), `ServerSessionInfo`
  (access/refresh token, expiry), `ServerUserInfo` (`isAdmin`, ...). `server.version: HomeAssistantVersion?`
  supports `isAtLeast(y, m, r)` for feature gating (AGENTS.md requires this gating).

### `AuthenticationRepository` (`common/…/data/authentication/AuthenticationRepository.kt`, impl `impl/AuthenticationRepositoryImpl.kt`)
- `buildBearerToken()` returns `"Bearer <token>"`. It calls `ensureValidSession()` first, which
  refreshes through `refreshSessionWithToken()` when `session.isExpired()`. `retrieveAccessToken()`
  works the same way. `retrieveExternalAuthentication(forceRefresh)` returns
  `{"access_token":…, "expires_in":…}`, the JSON the frontend's `externalAuthSetToken` expects.
- `getSessionState(): SessionState` (`CONNECTED`/`ANONYMOUS`), `revokeSession()`, app-lock helpers.
- The WebSocket core authenticates itself (it calls the auth repo internally), so callers never handle tokens for WS.

### `ServerConnectionStateProvider` (`common/…/data/servers/ServerConnectionStateProvider.kt`)
- `urlFlow(): Flow<UrlState>` resolves internal vs external URL (home SSID/network), `getApiUrls()`, `isInternal()`, `getSecurityState()`, `canSafelySendCredentials(url)`.
  Use it to build absolute URLs for camera proxies and `entity_picture`.

### Onboarding and launch
- `:app/…/launch/LaunchViewModel.handleInitialState()` → `connectToServer(SERVER_ID_ACTIVE, FrontendTarget.Default)`
  → `getServerConnectedAndRegistered()` (`isRegistered()` && `SessionState.CONNECTED`) → waits on
  `NetworkStatusMonitor` → `handleNetworkState()` sets `LaunchUiState.Ready(FrontendRoute(target, serverId))`
  (or `AutomotiveRoute`). With no server it sets `OnboardingRoute(...)` (`…/onboarding/OnboardingNavigation.kt`).
  When onboarding finishes, `HANavHost` calls `navController.navigateToFrontend(...)`.

**Answer:** yes. After normal onboarding, any `@HiltViewModel`/`@Inject` class (in `:app` or in a new
module that depends on `:common`) can do
`serverManager.webSocketRepository()` / `integrationRepository()` / `authenticationRepository()` and
get a fully authenticated, auto-refreshing connection to the active server. No extra auth work is needed. A
new screen only has to handle the "not registered" case, by redirecting to `LaunchActivity`, which runs onboarding.

---

## 3. WebSocket

### Layers
- `WebSocketCore` (`common/…/data/websocket/WebSocketCore.kt`) is **`internal` to `:common`**.
  It is the generic transport:
  - `sendMessage(request: Map<String, Any?>): RawMessageSocketResponse?` (also takes a `WebSocketRequest(message, timeout = 30s)`).
  - `subscribeTo<T>(type, data, timeout): Flow<T>?` returns a shared flow. Identical subscriptions are de-duplicated,
    and `unsubscribe_events` is sent when the last collector leaves (after `timeout`, default 0).
  - `sendBytes`, `ping()`, `getConnectionState()`, `shutdown()`.
  - `RawMessageSocketResponse` (`impl/entities/SocketResponse.kt`, internal) has `success`, `result: JsonElement?`, `error`.
- `WebSocketRepository` (`WebSocketRepository.kt`, impl `impl/WebSocketRepositoryImpl.kt`) is the
  **public** API. It has typed methods only and **no generic `sendMessage` or `subscribe`**. Relevant methods:
  - One-shot: `getConfig()` (`GetConfigResponse`: location, `unitSystem`, `timeZone`, `version`, `components`, ...),
    `getStates(): List<Entity>?` (`get_states`), `getCurrentUser()`, `getAreaRegistry()`, `getDeviceRegistry()`,
    `getEntityRegistry()`, `getEntityRegistryDisplay()` (`config/entity_registry/list_for_display`, 2023.3+),
    `getEntityRegistryFor(id)`, `getFloorRegistry()` (2024.3+), `getServices()`, `getTodos()`/`updateTodo()`
    (these use WS `call_service` with `return_response`), Assist, Matter/Thread.
  - Subscriptions: `getStateChanges()` (`subscribe_events state_changed`), `getStateChanges(ids)` (state trigger),
    **`getCompressedStateAndChanges()` / `getCompressedStateAndChanges(entityIds)`** (`subscribe_entities`),
    `getAreaRegistryUpdates()`, `getDeviceRegistryUpdates()`, `getEntityRegistryUpdates()`,
    `getTemplateUpdates(template)` (`render_template`), `getNotifications()`.
  - `getConnectionState(): WebSocketState` returns a **snapshot, not a Flow**. The states are `Initial`, `Authenticating`, `Active`,
    `Closed(reason = AUTH | CHANGED_URL | OTHER)`.

### Gap 1: no public generic command
Lovelace needs `lovelace/config` (with `url_path`, `force`), `lovelace/dashboards/list`,
`lovelace/resources`, `frontend/get_themes`, `frontend/get_translations`, `history/stream`,
`recorder/statistics_during_period`, `auth/sign_path`, `camera/stream`, `search/related` and others. None of them is wrapped.
The private `webSocketCore` inside `WebSocketRepositoryImpl` can't be reached from outside, and `WebSocketCoreFactory`
(internal) builds a **new** core per call, which would mean a second socket.

### Gap 2: event decoding is hard-coded
`WebSocketCoreImpl.handleEvent()` switches on the subscription `type`:
`subscribe_entities` → `CompressedStateChangedEvent`, `render_template` → `TemplateUpdatedEvent`,
`subscribe_trigger` → `TriggerEvent`, assist pipeline, and for `subscribe_events` only
`state_changed` / `area_registry_updated` / `device_registry_updated` / `entity_registry_updated`.
**Anything else is logged ("Unknown event type received") and dropped.** So
`subscribe_events{event_type: "lovelace_updated"}`, `themes_updated`, `history/stream` and similar can't
work without changing `WebSocketCoreImpl`.

**Smallest upstream-friendly fix** (a candidate upstream PR):
1. `WebSocketRepository`: add `suspend fun sendRawMessage(message: Map<String, Any?>): JsonElement?` and
   `suspend fun subscribeRaw(type: String, data: Map<String, Any?>): Flow<JsonElement>?`, with default
   bodies in the interface so the MockK-based fakes in `AssistViewModelTest`, `MatterManagerImplTest` and others keep compiling.
2. `WebSocketRepositoryImpl`: implement them as one-liners over `webSocketCore`, checking `success`
   before returning `result` (as the architecture skill requires).
3. `WebSocketCoreImpl.handleEvent()`: in the final `else`/unknown branches, emit `response.event`
   (raw `JsonElement`) instead of returning, or mark raw subscriptions by a flag in the request.
That is about 30 lines across 3 upstream files. All Lovelace-specific models and calls then live in our own module.

### Entity state model
- `Entity` (`common/…/data/integration/Entity.kt`): `entityId`, `state: String`, `attributes: Map<String, Any?>`,
  `lastChanged`/`lastUpdated: LocalDateTime`, lazy `domain`. Custom `EntitySerializer` guards non-string states.
  It has **no `context`** field and no `last_reported`.
- `CompressedStateChangedEvent` / `CompressedEntityState` / `CompressedStateDiff` / `CompressedEntityRemoved`
  (`impl/entities/CompressedEntity.kt`) map to the `subscribe_entities` `a`/`c`/`r` and `+`/`-` format. They provide
  `CompressedEntityState.toEntity(id)` and `Entity.applyCompressedStateDiff(diff)` (Entity.kt:466).
- `IntegrationRepository.getEntityUpdates()` / `getEntityUpdates(ids)` (`IntegrationRepositoryImpl` ~L500)
  picks `subscribe_entities` on 2022.4+ and falls back to `state_changed` or a trigger. It seeds itself with `get_states`, keeps a
  local map and emits a full `Entity` per change (`toEntityUpdates()`). **Removed entities are not
  emitted**: `Flow<Entity>` can't express removal. For a dashboard store we'd rather consume
  `getCompressedStateAndChanges()` directly and keep our own `Map<String, Entity>` (which handles `r`).
  Note the existing comment that fetching `get_states` *inside* an event handler deadlocks, because messages are processed sequentially.

### Reconnection and lifecycle (`WebSocketCoreImpl` class KDoc, L107-140)
- Calling `sendMessage` opens the connection lazily, and the connection then stays open.
- **When the last `subscribeTo` flow loses its collectors, the core sends `unsubscribe_events` and,
  if `activeMessages` is empty, closes the socket** (`createSubscriptionFlow` → `close(1001, …)`).
- On failure while subscriptions exist: reconnect after `DELAY_BEFORE_RECONNECT = 10s` with capped backoff up to 2 min,
  then **automatically resubscribe** all active subscriptions (`resubscribeActiveSubscriptions`). URL changes
  (internal ↔ external) trigger an immediate reconnect (`handleUrlChangeWhileConnected`). Auth failure stops retries.
- `ping()` detects silently dropped sockets. Sends `supported_features` on 2022.9+.
- After a reconnect, `subscribe_entities` re-sends the full `a` set, so a store keyed by entity id converges again.
- Consequence for the UI: collect subscriptions with `WhileSubscribed(5_000)` in the ViewModel so
  rotation doesn't tear the socket down. Expose our own connection-state flow, for example by polling
  `getConnectionState()` or deriving it from subscription health, since none exists upstream.

### Reusable models for Lovelace (all public `data class`es in `…/websocket/impl/entities/`)
`AreaRegistryResponse`, `DeviceRegistryResponse`, `EntityRegistryResponse` (+`EntityRegistryOptions`
with sensor `displayPrecision`), `EntityRegistryDisplayResponse`/`Entry`, `FloorRegistryResponse`,
`GetConfigResponse`, `CurrentUserResponse`, `*RegistryUpdatedEvent`, `TemplateUpdatedEvent`, `DomainResponse`
(services). These cover strategy dashboards (areas/floors), the tile card name/area resolution, and `unit_system`.

---

## 4. REST, service calls and existing native entity UI

### `IntegrationRepository` (`common/…/data/integration/IntegrationRepository.kt`)
- `callAction(domain, action, actionData: Map<String, Any?>)` POSTs the mobile_app **webhook**
  `call_service` (`CallServiceIntegrationRequest`), tried over the URLs (`callWebhookOnUrls`). It does not go over WS and returns no response data.
  This is what widgets, controls, tiles and Wear all use. It throws `IntegrationException` on failure.
- `getEntities()` (WS `get_states`, sorted), `getEntity(id)` (REST `GET api/states/<id>` with bearer),
  `getEntityUpdates(...)`, `renderTemplate()`, `getTemplateUpdates()`, `getServices()`, `getConfig()`,
  `fireEvent()`, `isHomeAssistantVersionAtLeast()`.
- There is no class called `ServerIntegrationApi`. Retrofit lives in `impl/IntegrationService.kt`, and the shared OkHttp/Retrofit
  instances come from `common/…/data/HomeAssistantApis.kt` (`getOkHttpClient()`, which includes TLS client cert support).

### Press and toggle logic already written
- `EntityDisplay.onPressed(integrationRepository)` (Entity.kt:1283) maps the domain to the right service
  (lock/unlock, alarm, `press` for buttons, `turn_on/off` for fan/switch/script/input_boolean, scene,
  else `toggle`). `onEntityPressedWithoutState()` is a stateless variant. This is essentially the tile
  card's default `tap_action: toggle`/`more-info` logic.
- `app/…/controls/*Control.kt` (`HaControl.performAction`): light brightness, cover position, fan
  percentage, climate, media volume, lock, vacuum, all through `callAction`. These are good references for the
  service/attribute names, but they're tied to `android.service.controls`.
- Wear `HomePresenterImpl.onEntityClicked/onFanSpeedChanged/onBrightnessChanged/onColorTempChanged`
  (`wear/…/home/HomePresenterImpl.kt:89-160`) do the same thing again.

### Entity helpers in `:common` (very reusable, platform-free apart from `Context` for strings)
`Entity.kt`: `getIcon()` / `getStatelessIcon()` (a domain/device_class/state → MDI icon port of the
frontend logic, covering binary_sensor, cover and sensor), `isActive()`, `isExecuting()`, `getLightBrightness()`,
`getColorTemperature()`, `getLightColor()`, `getFanSpeed()`/`getFanSteps()`, `getCoverPosition()`,
`getClimateControls()`, `getNumberControls()`, `getMediaPlayerControls()`, `getVolumeLevel()`,
`getCoverControls()`, `getVacuumControls()`, `getCameraControls()`, `entityPicturePath()`,
`deviceClass()`, `unitOfMeasurement()`, `supportsFeature()` (internal), `EntityExt.DOMAINS_TOGGLE`/`DOMAINS_PRESS`.
`FriendlyState.kt`: `Entity.friendlyState(context, options, appendUnit)` / `FriendlyState` sealed type
(`Resource`, `Literal`, `RelativeTime`, `WithUnit`) with translated state strings for common domains and
sensor display precision. This is less complete than the frontend's `computeStateDisplay`: no number/locale
formatting of every unit, no `frontend/get_translations`.

### `EntitiesForDisplayManager` (`common/…/data/integration/display/EntitiesForDisplayManager.kt`)
This is the closest thing to a native "hass object". It is `@Inject`, runs on `Dispatchers.Default`, and offers:
- `observe(serverId, entityIds)` → `Flow<EntityDisplayState<EntityDisplayWithoutContext>>` (`Loading`/`Loaded`/`Error`),
  merging state changes and entity-registry updates.
- `observeInContext(serverId, filter)` → entity plus resolved **area, floor, device** (`EntityDisplayWithContext`),
  re-resolved on area/device/entity registry events.
- `snapshot(...)` variants.
- `EntityDisplay` (`display/EntityDisplay.kt`, `@Immutable`) provides `name` (registry-aware, which
  replaces the deprecated `friendlyName`), `icon`, `statelessIcon`, `state: FriendlyState`, `rawState`, `isActive`,
  `isExecuting`, `position`, `color`, `lightControls`, `fanControls`, `climateControls`, `coverControls`,
  `mediaPlayerControls`, `numberControls`, `vacuumControls`, `alarm`, ...
- Used by Wear `MainViewModel.observeEntities()` (`observeInContext`), Device Controls, Android Auto and widget configs.
- Caveats for a dashboard: every emission rebuilds the full list, `observe` re-fetches `get_states` via
  `integrationRepository.getEntities()`, and there is no per-entity flow. It's fine for a few hundred entities. For a big
  dashboard, build a dedicated `EntityStore` (a map plus `StateFlow` per entity id) and reuse the
  `EntityDisplay` resolution functions (`resolveEntityDisplayItems`, `itemFor`, which are private/internal today) or
  their logic.

### Existing native entity UI
- **`:wear` home** (`wear/…/home/views/EntityUi.kt`, `EntityListView.kt`, `DetailsPanelView.kt`,
  `MainView.kt`; VM `home/MainViewModel.kt`) renders entities grouped by area/domain with toggles and
  brightness/fan/color-temp sliders. It is built on `androidx.wear.compose.material3` / `wear.compose.material`
  (`ToggleChip`, `Button`), so **the composables can't be reused on the phone**. The VM pattern (grouping
  by area/domain from `EntityDisplayWithContext`) and the presenter's service-call mapping are reusable ideas.
- `:app` composables built on `EntityDisplay`: `util/compose/entity/EntityPicker.kt`,
  `util/compose/FavoriteEntityRow.kt`, `settings/controls/views/ManageControlsView.kt`, plus Android Auto
  `vehicle/EntityGridVehicleScreen.kt` (Car App templates, not Compose). These are picker/list rows, not cards.
- Glance widgets (`widgets/entity`, `widgets/BaseGlanceEntityWidgetReceiver.kt`) use
  `integrationRepository.getEntityUpdates(entityIds)`. They are RemoteViews/Glance and not reusable as UI.
- **No M3 phone composable for an entity tile, card, slider or more-info exists. All card UI has to be built new.**

---

## 5. Room database

- `common/…/database/AppDatabase.kt`: `internal abstract class AppDatabase : RoomDatabase()`,
  `DATABASE_VERSION = 53`, DB file `"HomeAssistantDB"` (`DatabaseModule.kt`, `@Singleton` and `internal object`).
  Entities: `Server`, `Setting`, `Sensor`/`Attribute`/`SensorSetting`, `Authentication`, the widget tables
  (`ButtonWidgetEntity`, `CameraWidgetEntity`, `StaticWidgetEntity`, `TodoWidgetEntity`, `TemplateWidgetEntity`,
  `MediaPlayerControlsWidgetEntity`), `NotificationItem`, `LocationHistoryItem`, and Wear (`Favorites`,
  `FavoriteCaches`, `CameraTile`, `ThermostatTile`, `EntityStateComplications`, `TileEntity`).
- Migrations: `AutoMigration(from, to[, spec])` list (24→53) plus manual `migrationPath(context)`. Exported schemas live in
  `common/schemas/` (`ksp arg room.schemaLocation`), and the androidTest assets read them.
- **There is no entity-state cache.** `StaticWidgetEntity` stores only the widget config plus a `lastUpdate`
  string, and `FavoriteCaches` stores Wear favourites' name and icon. State is always live from WS/REST.
- **Adding our tables:** do not touch `AppDatabase`. Bumping `DATABASE_VERSION`, adding to the `entities = [...]`
  list and adding `common/schemas/…/54.json` would collide with every upstream schema bump, since the version number is shared
  and linear. Instead, create a **separate Room 3 database in our module**, for example `DashboardDatabase`
  (file `"HADashboardDB"`) with `dashboard_config(server_id, url_path, json, fetched_at)` and
  `entity_state_cache(server_id, entity_id, state_json, last_updated)`, its own `@Module` providing DAOs, its own
  schema dir and its own version line. This is fully isolated with zero upstream edits. Clean up on server removal by observing
  `serverManager.serversFlow` and deleting orphaned `server_id` rows. `ServerManagerImpl.removeServer` only cleans
  its own tables.
  For the plain config JSON, DataStore or a file per dashboard is also fine. The architecture skill
  says to use DataStore for new key/value storage.

---

## 6. Frontend (WebView) screen and navigation

### Structure (`app/…/frontend/`)
- `navigation/FrontendNavigation.kt`: `@Serializable FrontendRoute(rawPath, serverId) : HAStartDestinationRoute`,
  `NavController.navigateToFrontend(target, serverId)`, `NavGraphBuilder.frontendScreen(...)` (about 15 callbacks).
  `navigation/FrontendTarget.kt`: sealed `Default`, `Path("/lovelace/0")`, `EntityMoreInfo(entityId)`.
- `FrontendScreen.kt` (`FrontendScreen` → `FrontendScreenContent`, `SafeHAWebView`, `WebView.configureForFrontend`,
  overlays for error, insecure and security level) plus `FrontendViewModel.kt` (60 KB, about 20 injected collaborators:
  `HAWebViewClientFactory`, `FrontendJsBridgeFactory`, `FrontendUrlManager`, `FrontendExternalBusRepository`,
  `FrontendDialogManager`, download/file chooser/ExoPlayer/Improv/barcode/Matter-Thread handlers, ...).
- `util/compose/webview/HAWebView.kt`: **public** `@Composable fun HAWebView(onWebViewCreationFailed, modifier,
  configure, factory, onBackPressed, nightModeTheme)` (`AndroidView` wrapper with a placeholder in preview mode).
  `EXTERNAL_AUTH_QUERY_PARAM = "external_auth"`. `util/HAWebViewClient.kt`: `HAWebViewClientFactory`
  (TLS client certs via `KeyChainRepository`).
- `js/FrontendJsBridge.kt` (`@AssistedInject`, takes a `FrontendJsHandler`, scope and `stateProvider: () -> BridgeState`)
  registers V1 `window.externalApp` (`addJavascriptInterface`) or V2 `externalAppV2` (`WebMessageListener`,
  origin and main-frame checked, server ≥ 2026.4.2). It routes `getExternalAuth` / `revokeExternalAuth` / `externalBus`
  to `FrontendJsHandler`. `handler/FrontendMessageHandler.kt` is the implementation, bound in
  `FrontendHandlerModule` (`ViewModelComponent`). `session/ServerSessionManager.getExternalAuth()` builds
  `externalAuthSetToken(true, {...})` from `retrieveExternalAuthentication()`.
- `url/FrontendUrlManager.serverUrlFlow(serverId, target)` resolves base URL plus path, appends `external_auth=1`,
  and handles the security-level gate.
- `webview/WebViewActivity.kt` is deprecated (`level = ERROR`) and only redirects legacy shortcuts.
  `FrontendScreen` *is* the production path now. `WIPFeature.kt` contains only `USE_SHORTCUTS_V2`.

### Navigation and launch
- Single activity `launch/LaunchActivity` → `setContent { HATheme { HAApp(navController, startDestination, …) } }`.
  `util/compose/HAApp.kt` (Scaffold, snackbar) → `util/compose/HANavHost.kt` (`NavHost` with `onboarding(...)`,
  `wearOnboarding`, `frontendScreen(...)`, `changelogScreen`, `setHomeNetworkScreen`, `carAppActivity`).
- The start destination is chosen in `LaunchViewModel.handleNetworkState()` (`FrontendRoute(target, serverId)`).
- Settings is still the legacy `SettingsActivity` + `SettingsFragment` (PreferenceFragment, `res/xml/preferences.xml`,
  `preferences_developer.xml`). Launch helpers (`startLaunchWithNavigateTo`, `LaunchActivity.newInstance`,
  `DeepLink.NavigateTo`) are **`internal` to `:app`**. From another module, use the public deep link
  `homeassistant://navigate/<path>` (`launch/link/LinkHandler.kt`, `NAVIGATE_URL_PATH`).

### Where a native dashboard plugs in (ordered by how few upstream edits it needs)
1. **Separate entry Activity in our module (zero edits to nav/launch files).** Our module ships
   `DashboardActivity` (`@AndroidEntryPoint`, `setContent { HATheme { DashboardNavHost() } }`) and declares it, plus an
   optional `<activity-alias>` launcher icon or a static shortcut, in **its own `AndroidManifest.xml`**, which manifest
   merge adds to the app. On start it checks `serverManager.isRegistered()` and the session state. If either fails it starts
   `LaunchActivity` (by component/action, `ACTION_MAIN`) for onboarding. "Open in web" or an unsupported view sends an
   `ACTION_VIEW homeassistant://navigate/lovelace/<path>` to the existing WebView screen. A settings toggle can live
   inside our activity's own settings screen (DataStore) instead of `SettingsFragment`.
2. **Route inside the existing graph (best UX, small edits).** Add `@Serializable DashboardRoute(serverId, urlPath)
   : HAStartDestinationRoute` and `NavGraphBuilder.nativeDashboardScreen(...)` in our module, then:
   - `HANavHost.kt`: one call `nativeDashboardScreen(navController, onOpenInWeb = { navController.navigateToFrontend(FrontendTarget.Path(it)) })`.
   - `LaunchViewModel.handleNetworkState()`: `if (dashboardPrefs.useNative) DashboardRoute(...) else FrontendRoute(...)`
     (needs one injected dependency, so the `@AssistedInject` constructor plus its test change too).
   - `HAApp.kt` treats `FrontendRoute` specially for edge-to-edge insets, so ours would need the same handling or would handle insets itself.
   - The onboarding `onOnboardingDone` in `HANavHost` should also respect the pref.
   That is 2-3 upstream files, in hot ones (`LaunchViewModel`, `HANavHost`).
3. A "native dashboard" toggle in `preferences.xml` / `SettingsFragment` costs 2 more legacy files plus strings.

### WebView fallback card/view
- The full `FrontendScreen`/`FrontendViewModel` can't be embedded as a card: it is a whole screen, `internal`,
  and owns system bars, insets and back handling.
- A lightweight fallback is feasible from our module **if it lives in `:app`'s classpath**: `HAWebView` (public)
  plus `HAWebViewClientFactory` (public, `@Inject`) plus `FrontendJsBridge` with **our own `FrontendJsHandler`** that
  only implements `getExternalAuth`/`revokeExternalAuth` via `serverManager.authenticationRepository(id)
  .retrieveExternalAuthentication(force)` and `webView.evaluateJavascript("externalAuthSetToken(true, $json)")`,
  ignoring `externalBus` (or answering `config/get`). Load `<base>/<dashboard>/<view>?external_auth=1`.
  `FrontendJsBridge` is in `:app`, so a separate library module can't depend on it (`:app` is an application).
  Options: (a) copy the roughly 60 lines of V1 `addJavascriptInterface("externalApp")` auth handling into our module; it
  depends only on `:common` and is safe as long as we accept only our server's origin. (b) Put the fallback glue in an
  `:app` source file we add (new file, no conflict).
- A full HA frontend boot inside a card is heavy (it loads the whole SPA per WebView). For an unsupported *card*, prefer
  a placeholder with "open in web" (deep link) and use a WebView fallback only per *view*. Custom (HACS) cards render
  only in the full frontend anyway.

---

## 7. Theming

- `common/…/common/compose/theme/HATheme.kt`: `HATheme(darkTheme = isSystemInDarkTheme())` wraps
  **Material 3 `MaterialTheme`** and provides `LocalHAColorScheme` (`HAColors.kt`: `LightHAColorScheme`/
  `DarkHAColorScheme`, tokens like `colorSurfaceDefault`, `colorOnPrimaryNormal`, `colorTextPrimary`, ...). It only
  overrides a handful of M3 roles (`surface`, `background`, `surfaceContainerLow/High`, `primary`, `inverse*`).
  `HATextStyle.kt`, `HASize.kt` (`HADimens.SPACE*`), `HARipple.kt`. `HAThemeForPreview` is used for previews and screenshots.
- **No dynamic color (Material You)**: there is no `dynamicLightColorScheme` anywhere, and the scheme is fixed to HA brand colors.
- Components (`common/…/compose/composable/`): `HAButtons`, `HASwitch`, `HACheckbox`, `HARadioGroup`,
  `HATextField`, `HASearchField`, `HADropdownMenu`, `HAModalBottomSheet`, `HATopBar`, `HABanner`, `HADetails`,
  `HAInputChip`, `HALabel`, `HAProgress`, `HAFloatingActionButton`, `HASettingsCard`, `HADivider`.
  There is **no card, slider, tile, chip-row or grid component** to build cards from.
- The UI skill forbids raw M3 components "if an HA* wrapper is missing" (for upstream PRs). In our fork we can choose:
  build cards on raw M3 (`Card`, `Slider`, `FilledTonalButton`) with our own `DashboardTheme`, which maps HA frontend
  theme variables from `frontend/get_themes` and optionally `dynamicColorScheme` on Android 12+ to an M3 `ColorScheme`.
  Wrapping inside `HATheme` keeps snackbars and dialogs consistent. Recommendation: our own theme layer inside the module,
  falling back to `HATheme` colours.

---

## 8. Testing infrastructure

- Unit tests: JUnit 5 (Jupiter) by default, JUnit 4 plus vintage for Robolectric. MockK. `kotlinx-coroutines-test`. Turbine
  is mandatory for Flows. All of this is wired by `AndroidCommonConventionPlugin`, so a new module gets it for free.
- Robolectric 4.17, SDK pinned through a generated `robolectric.properties` (`sdk=37`). Every Robolectric test class needs
  `@RunWith(RobolectricTestRunner::class)` + `@Config(application = HiltTestApplication::class)`.
- Module-wide rules via ServiceLoader: `common/src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`
  → `TestStateResetPlatformListener`, plus a Robolectric plugin `TestStateResetRobolectricPlugin` (FailFast → AssertionError,
  `SdkVersion` reset). A new module should copy these two service files and listeners. They live in `:common`'s **test**
  source set, so they aren't shared.
- `:testing-unit`: `MainDispatcherJUnit5Extension` / `MainDispatcherJUnit4Rule`, `FakeClock`, `TestSharedFlow`,
  `ConsoleLogPlatformListener`, `AndroidComposeTestRule.stringResource`, `seedFakeAndroidId`, Wear fakes.
- Screenshot tests: the Compose Preview Screenshot plugin, sources in `src/screenshotTest/`, references in
  `src/screenshotTestDebug/reference` (`:common`, `:wear`) / `src/screenshotTestFullDebug/reference` (`:app`).
  Run `./gradlew validateDebugScreenshotTest` / `updateDebugScreenshotTest`. Threshold `0.00025`. **This fits Lovelace cards
  well**: a `@PreviewTest` per card state.
- WebSocket tests: `common/src/test/…/data/websocket/impl/WebSocketCoreImplTest.kt` (93 KB) and
  `WebSocketRepositoryImplTest.kt` use MockK on `OkHttpClient`/`WebSocket`, not MockWebServer, which isn't in the catalog.
  `EntitiesForDisplayManagerTest.kt` (45 KB), `EntityTest.kt` and `FriendlyStateTest.kt` show how to build `Entity`
  fixtures. There are no shared JSON fixture files. For Lovelace, add our own `src/test/resources/lovelace/*.json`
  (real `lovelace/config` dumps) and parse them in tests.

---

## 9. Recommendation: fork in place, as a new Gradle module

**Fork in place.** A separate repo would have to re-implement or vendor `ServerManager`, auth refresh, the WebSocket
core (reconnect/resubscribe), TLS client certs, onboarding (local discovery, mTLS, home network) and Hilt wiring.
That is tens of thousands of lines, all of which keep evolving upstream. Inside the repo we get them through
`implementation(project(":common"))`.

### Layout
```
dashboard/                                   # new module :dashboard (Android library)
  build.gradle.kts                           # android.library + homeassistant.android.common + .compose
  gradle.lockfile                            # generated
  src/main/AndroidManifest.xml               # DashboardActivity (+ optional launcher alias)
  src/main/kotlin/io/homeassistant/companion/android/dashboard/
    data/   LovelaceRepository, models (@Serializable config/cards), DashboardDatabase (Room 3), EntityStore
    domain/ use cases (resolve view, strategy expansion, action handling)
    ui/     DashboardActivity, DashboardNavHost, DashboardViewModel, cards/*, theme/DashboardTheme
    webfallback/ minimal external-auth WebView (copy of V1 bridge auth subset)
  src/main/res/values/strings.xml            # our own strings → no conflicts with common strings.xml
  src/test, src/screenshotTest, src/test/resources/META-INF/services/…
```
Notes:
- Depend only on `:common`. Do not depend on `:app`, which is an application module. Everything we need is public in `:common`
  (`ServerManager`, repositories, `Entity` + helpers, `EntitiesForDisplayManager`, `HATheme`, `HA*`, `kotlinJsonMapper`,
  `MapAnySerializer`, registry models, `HomeAssistantApis`).
- `:common` strings are reachable as `io.homeassistant.companion.android.common.R`. Keep our new strings in our own
  `res/` to avoid conflicts with `strings.xml`, which upstream edits almost daily. This deliberately differs from upstream
  policy, so move strings over if we ever upstream.
- Use only libraries already in the catalog (Compose, Navigation, Hilt, Room 3, Coil 3, kotlinx.serialization).
  Then `:app`/`:automotive` lockfiles don't change when we add the module, and our only lockfile is `dashboard/gradle.lockfile`.
  If a new library is unavoidable, add it to `libs.versions.toml` at the **end of each section** to reduce conflict hunks.

### Minimum upstream files to touch
| File | Change | Conflict risk |
|---|---|---|
| `settings.gradle.kts` | add `":dashboard"` to `include(...)` | low (list rarely changes) |
| `app/build.gradle.kts` **and** `automotive/build.gradle.kts`, or a single line in `build-logic/…/AndroidApplicationDependenciesConventionPlugin.kt` | `implementation(project(":dashboard"))`. Only needed so the module's manifest and code get packaged; `:automotive` needs it only if `:app/main` code references our classes | low |
| `common/…/websocket/WebSocketRepository.kt`, `impl/WebSocketRepositoryImpl.kt`, `impl/WebSocketCoreImpl.kt` | generic `sendRawMessage` / `subscribeRaw` plus the raw-event fallback in `handleEvent` (§3) | medium (`WebSocketCoreImpl` is actively changed); **upstream this as a PR**, it's generic and small |
| *(optional, route integration)* `util/compose/HANavHost.kt`, `launch/LaunchViewModel.kt` (+ its test) | add the route and the start-destination switch | medium-high; skip in phase 1 by using the separate `DashboardActivity` entry |
| `app/gradle.lockfile`, `automotive/gradle.lockfile` | only if our module adds new external deps | high; avoid new deps |

That is **4-6 upstream files for phase 1, and 3 of them are a candidate upstream PR**. Nothing in `AppDatabase`,
`strings.xml`, `preferences*.xml`, `SettingsFragment`, `FrontendScreen` or `FrontendViewModel`.

### Keeping the surface small
- Keep every fork change behind clearly marked one-line hooks (`// FORK(dashboard): …`) so merge conflicts are
  obvious and trivially resolvable. Never reformat upstream files: run ktlint only on our module
  (`./gradlew :dashboard:ktlintFormat`).
- Prefer manifest merging, Hilt multibinding and our own `@Module`s over editing upstream DI modules.
- Upstream the generic WebSocket API (and later perhaps the `FrontendJsBridge` "auth-only" mode). Every hook that lands
  upstream is one less conflict.
- When lockfiles conflict after merging upstream: take upstream's version and run `./gradlew alldependencies --write-locks`.
- Watch for breakage on merges rather than conflicts. `EntitiesForDisplayManager`, `Entity` helpers and `FriendlyState`
  are refactored often, which shows in recent deprecations like `friendlyName`. Keep our use of them behind a thin adapter
  (`EntityDisplayAdapter`) so API churn is fixed in one place. Detekt and lint run on our module too (`lint-baseline.xml`,
  `warningsAsErrors`), so add a `dashboard/lint-baseline.xml` if needed.

---

## 10. Building and running locally

- **JDK 21** (CI: `.github/actions/setup-build-env/action.yml`, Temurin 21). This machine's system Java is 25.
  Point `JAVA_HOME` at a 21 JDK (one is already cached at `~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`). The bytecode
  target is Java 11.
- Android SDK with platform 37 (`compileSdk = 37`). `ANDROID_HOME=~/Android/Sdk` already has `android-37.0`. Configuration
  also triggers an **NDK 29.0.14206865 install** (`:microwakeword` has native code). Gradle auto-installs it on first run
  if licenses are accepted, which happened here.
- **`google-services.json`:** `AndroidApplicationConventionPlugin` applies the `google-services` plugin to `:app`,
  `:automotive` and `:wear` for **both flavors**, so assembling any app variant needs a json file. CI copies the mock:
  `cp .github/mock-google-services.json app/google-services.json` (also into `wear/` and `automotive/` if you build those).
  FCM push then won't work, but nothing else is affected.
- Recommended dev build: `./gradlew :app:assembleMinimalDebug` (FOSS, no Play Services, package
  `io.homeassistant.companion.android.minimal.debug`), installable alongside the store app. Use `:app:installMinimalDebug`
  to deploy. Add `-PnoLeakCanary` to skip LeakCanary and `-Pabis=arm64-v8a` (or `x86_64` for the emulator) to speed up native packaging.
- `./gradlew :common:test` (or the faster `:common:testDebugUnitTest`) **does not need google-services.json**. `:common` is a
  library. It only needs JDK 21, the SDK and the NDK download. See the result note below.
- Formatting and checks before committing: `./gradlew :build-logic:convention:ktlintFormat ktlintFormat`,
  `detektMain`, `lint`.

> Local verification on this machine: `JAVA_HOME=<jdk21> ./gradlew :common:testDebugUnitTest` gave **BUILD SUCCESSFUL in 7m 27s** on a cold run. About 889 tests passed across about 130 result files.
> No google-services.json, `local.properties` or other setup was needed. The first run auto-installed NDK 29.0.14206865.

---

## Reuse verdict

| Component | Verdict | Notes |
|---|---|---|
| `ServerManager` (multi-server, active server) | **Reuse as-is** | inject it; `SERVER_ID_ACTIVE` |
| `AuthenticationRepository` (bearer, refresh, external auth JSON) | **Reuse as-is** | `buildBearerToken()`, `retrieveExternalAuthentication()` |
| Onboarding / launch flow | **Reuse as-is** | redirect to `LaunchActivity` when not registered |
| `ServerConnectionStateProvider` (internal/external URL) | **Reuse as-is** | for image and camera URLs |
| `WebSocketCore` (reconnect, resubscribe, auth) | **Reuse as-is** | transport is solid |
| `WebSocketRepository` typed calls (`get_states`, registries, `get_config`, `render_template`, `subscribe_entities`) | **Reuse as-is** | |
| Generic WS command / subscription (`lovelace/config`, `lovelace_updated`, `history/stream`, themes) | **Reuse with extension** | ~30-line patch to 3 files; upstream PR candidate |
| `Entity`, `CompressedEntity*`, `applyCompressedStateDiff` | **Reuse as-is** | no `context`; our store handles removals |
| `IntegrationRepository.getEntityUpdates` | **Reuse with extension** | no removal signal; prefer our own store over `getCompressedStateAndChanges()` |
| `IntegrationRepository.callAction` (webhook call_service) | **Reuse as-is** | no response data; WS `call_service` needs the generic command |
| `EntityDisplay.onPressed`, entity control helpers (`getLightBrightness`, `getClimateControls`, ...) | **Reuse as-is** | |
| `getIcon()` / `FriendlyState` / `friendlyState()` | **Reuse with extension** | wrap and add `frontend/get_translations` / number formatting later |
| `EntitiesForDisplayManager` / `EntityDisplayWithContext` (name/area/floor/device) | **Reuse with extension** | fine for v1; a large dashboard needs a per-entity store |
| Registry models (area/device/entity/floor/display) | **Reuse as-is** | |
| Lovelace config models, view/section/card parsing, strategies | **Build new** | nothing exists |
| Card composables (tile, entities, glance, button, gauge, thermostat, history-graph, …) | **Build new** | Wear UI is Wear-Compose only; `:app` has no card UI |
| More-info dialogs / controls (sliders, color, climate) | **Build new** | logic reuse from `controls/*Control.kt`, Wear presenter |
| `HATheme` / `HA*` components | **Reuse with extension** | M3-based, no dynamic color, no card/slider wrappers; add a dashboard theme layer |
| Coil image loading (HA OkHttp client) | **Reuse as-is** | add `Authorization` header via `NetworkHeaders` (see `ServerUserAvatarUseCase`) |
| Room `AppDatabase` | **Build new (separate DB)** | do not edit `AppDatabase`; own `DashboardDatabase` in our module |
| Navigation (`HANavHost`, `FrontendRoute`) | **Reuse with extension** (phase 2) | phase 1: own `DashboardActivity`, zero nav edits |
| WebView fallback (`HAWebView`, `HAWebViewClientFactory`, `FrontendJsBridge`) | **Reuse with extension** | the bridge is in `:app`; copy the auth-only V1 subset or add an `:app` file |
| `FrontendScreen` / `FrontendViewModel` | **Do not reuse** for cards | keep as "open in web" target via `homeassistant://navigate/…` |
| Test infra (JUnit5, MockK, Turbine, Robolectric, screenshot plugin, `:testing-unit`) | **Reuse as-is** | copy the two `META-INF/services` listeners into our module |
