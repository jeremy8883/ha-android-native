# Native Dashboard: Architecture & Milestone 1 Plan

This doc pulls together `docs/investigation/{frontend-lovelace,android-reuse,core-backend}.md`. Those docs hold the source
citations; this one holds the decisions.

**Port reference:** frontend `20260624.6` (ships with HA 2026.7.x). Core and frontend `main` are used to spot upcoming
drift.

## 1. Upstream architecture in one page

**Server (core).** Dashboards are JSON blobs served over WebSocket:
- `lovelace/dashboards/list` returns metadata.
- `lovelace/config {url_path}` returns the config. Storage and YAML dashboards look identical to a client.
- `lovelace_updated {url_path}` fires on save, delete and YAML reload.
- Entity state streams through `subscribe_entities`: a compressed snapshot, then `+`/`-` diffs.
- Registries (entities `list_for_display`, devices, areas, floors) are fetched, then re-fetched when a
  `*_registry_updated` event arrives.
- The server knows **nothing** about how cards render.

**Frontend.** Everything visual is client-side:
- Element types resolve to `hui-<type>-card`, `-row`, `-badge`, `-section`, `-view` and so on.
- Views are `masonry` (the default), `sections`, `panel` or `sidebar`.
- **Strategies** are plain TS functions run on the client: `(strategy config, hass) → dashboard/view/section config`.
- Visibility conditions are evaluated on the client at 20260624.6. On `main` they are moving to the server
  (`subscribe_condition`).
- Display logic is pure-ish TS that's worth porting: state display and translations, icons, colours, names, feature
  bitmasks, `entity_filter`.

**The default dashboard is generated.** From 2026.x:
- If no `lovelace` dashboard is stored, the default panel is **`/home`**.
- `/home` is a frontend-only panel. It runs the `home` strategy over the registries, states, `frontend/get_system_data
  {key:"home"}` and `usage_prediction/common_control`.
- So for a typical new install there is no stored config to load at all, and **porting `home` is required for M1**.

**Android app.** It's a WebView shell around the frontend, plus native integrations.
- `:common` already provides `ServerManager` (multi-server, auth, token refresh), `WebSocketRepository` (with
  `subscribe_entities` compressed diffs, reconnect and backoff), REST/webhook service calls, Room, and Hilt.
- Existing entity helpers include `EntityDisplay`, `getIcon` and `FriendlyState`.
- No phone card UI exists. Wear OS uses Wear-only components.

## 2. Fork vs separate repo: **fork in place, new modules**

Fork `home-assistant/android` (done) and add new modules. The reasons:
- Auth, multi-server, WebSocket, reconnection and notifications all come for free.
- Building separately would mean re-implementing or vendoring `:common`, which is worse to maintain.

Rules to keep the merge-conflict surface tiny:
- New code lives only in new modules. Edits to upstream files are limited to:
  - `settings.gradle.kts`
  - one `implementation(project(":dashboard"))` line in `app/build.gradle.kts`
  - a small generic WS patch to `:common`. It adds a raw `sendMessage(JsonObject)` / `subscribe(type, JsonObject)`
    and passes unknown event types through. These changes are generic enough for the user to offer upstream later.
- The module gets its own manifest-declared `DashboardActivity`, its own strings and its own `DashboardDatabase`
  (Room). Never touch `AppDatabase`.
- Don't add libraries to `:app` or `:automotive`, because lockfiles are global.
- Wiring into `HANavHost`/`LaunchViewModel` waits until a later phase (those files change often). Until then, launch
  via a launcher shortcut / `DashboardActivity`.

## 3. Module structure

```
:dashboard-core   pure Kotlin/JVM. No Android, no :common. Fast unit tests, easy differential tests.
  model/          Config wrappers over JsonObject: LovelaceConfig, View, Section, Card, Badge, Action, Condition
                  (typed accessors; unknown keys always preserved)
  entity/         EntityState (raw), registries (EntityEntry, Device, Area, Floor), compressed-state decode + diff apply
  derive/         pure fns: stateDisplay, icon, stateColor, name, supportsFeature, entity_filter, entity context
  condition/      visibility evaluation (state, numeric_state, screen, user, and/or/not, location?)
  strategy/       ports of home/*, areas helpers, common-controls (pure: HassSnapshot → LovelaceConfig)
  layout/         sections grid sizing, masonry column distribution (pure)
  // each ported file has a header: `// Port of frontend@20260624.6 src/...ts`

:dashboard        Android library. Depends on :common and :dashboard-core.
  data/           DashboardRepository: raw StateFlows from WS/Room, connection status, cache write-through
  db/             DashboardDatabase (Room): cached raw config JSON, raw entity states, registries, fetchedAt
  ui/             Compose. DashboardActivity, view/section/card composables  ← UI-heavy work goes to Astra
  ui/fallback/    WebView fallback card/view (reuses HAWebView, auth-only external-bus subset)
```

`:dashboard-core` being pure JVM is the key maintainability lever:
- Strategies, conditions and derivations are tested against JSON fixtures in milliseconds.
- They can also be compared against golden output from the real TS (§7).

## 4. State flow

```
WS / Room ──► Raw state (single source)                  Derived (pure, never stored)              UI
              HassSnapshot(                                 resolvedDashboard = config ?: strategy(snapshot)
                states: Map<id, EntityState>,               visibleCards = conditions(view, snapshot, screen)
                entities, devices, areas, floors,           cardModel = derive(cardConfig, states[e])
                lovelaceConfig: JsonObject?,                                                         ──► Compose
                systemData, user, haVersion,
                connection: Live | Cached(at) | Connecting)
```

- One `StateFlow<HassSnapshot>` per server and dashboard. Mutable state exists only for transient input
  (slider drag, text field).
- Structure derives from config + registries only, so a state change never regenerates the dashboard. This matches
  upstream's regeneration triggers.
- Cards read `states[entityId]` and derive their model; keyed lookups and stable immutable models keep
  recomposition minimal.
- Actions → `:common` service call → server → state diff → UI. No optimistic duplicate state.
- Offline, controls stay enabled: an action fails and shows the failure message, as upstream. There is no command queue.
- **Failures are never empty data.** Every piece of server data is a `Loadable` (`data/Loadable.kt`): `Loading`,
  `Ready(value, refreshing, refreshError)` or `Failed(error)`, with a typed `LoadError` (no server, no response,
  server error, unexpected response). `KeptData` (`data/KeptData.kt`) loads it when collected, again on change events,
  after every reconnection (`WebSocketRepository.connectionStatus()` counts connections) and after retryable failures
  with backoff; a failed attempt keeps the loaded value. The last value of each piece is kept per server
  (`LoadedData`): in memory, and in the dashboard cache (`data/cache/`, its own Room database, so it never touches the
  app's migrations), so a screen collected again, or the app started again, starts from it, even offline. What is
  cached is the raw server responses (bundled by piece, `ServerResponses.kt`), parsed the same way as when loaded;
  entity states are one row per entity in the compressed `subscribe_entities` form, written only when they change;
  template renderings are cached too. Writes are batched (2s). A server's cache is removed with the server (logging
  out removes it); there is no expiry, and the offline bar says how old the data is.
  The first `subscribe_entities` event after a reconnection is a full snapshot and replaces the states.
- **Screen states:** nothing loaded → spinner; nothing could be loaded → error with Retry; loaded data being loaded
  again → thin bar under the top bar; connection lost (after 1s, like the frontend's toast) → a bar at the bottom,
  "Connection lost. Reconnecting… · Updated …"; a refresh refused by the server → a message with Retry. Indicators
  float over the content so nothing moves; the scrolling content keeps room at its end for the bottom bar.
- **Dev safety:** a debug `readOnly` flag blocks all service calls.

## 5. APIs needed for M1

| Purpose | Command | Notes |
|---|---|---|
| auth | `auth` / `auth_ok` (via `:common`) | `ha_version` in hello |
| server info | `get_config`, `auth/current_user` | version gating, `is_admin`, user conditions |
| panels | `get_panels` | sidebar list: `home`, `lovelace`, dashboards, with title/icon overrides; respects admin |
| dashboards | `lovelace/dashboards/list` | mode, url_path, id (`slugify(url_path)`) |
| config | `lovelace/config {url_path, force:false}` | `config_not_found` → upstream fallback (§1) |
| config changes | `subscribe_events lovelace_updated` | refetch on matching url_path |
| states | `subscribe_entities` | `a` snapshot, `c` (`+`/`-`), `r`. `lu` omitted when it equals `lc`; `c` (context) may be a string |
| registries | `config/entity_registry/list_for_display`, `config/{device,area,floor}_registry/list` + `*_registry_updated` | compact keys; disabled entities excluded |
| home strategy | `frontend/get_system_data {key:"home"}`, `usage_prediction/common_control`, `energy/get_prefs` | |
| markdown | `render_template` subscription | |
| translations / icons | `frontend/get_translations`, `frontend/get_icons` | plus frontend bundle strings shipped in the app |
| actions | `call_service` (WS) or the existing webhook `callAction` | **writes: gated** |

## 6. Inventory and M1 scope

Full tables are in frontend-lovelace.md §4 and §12.8. M1 targets:
- **Views:** `sections` (needed by `home`), `masonry`, `panel`. `sidebar` comes later.
- **Sections:** `grid`.
- **Cards:**
  - Required by the brief: entities, tile, button, sensor (no graph at first), gauge, glance, light, thermostat,
    markdown, conditional.
  - Required by `home`: heading (+ heading badges), area (compact), empty-state, home-summary (no energy at
    first), plus the vertical/horizontal stack and grid cards.
- **Tile features:** toggle, light-brightness, cover-open-close, target-temperature, fan-speed.
- **Rows:** simple, toggle, sensor, plus divider, section and weblink.
- **Fallback:** an "unsupported card" placeholder first, then a WebView-backed card or view.
- **Custom cards** (`custom:*`, HACS) can't be native. The WebView fallback is the only path, and `extra_module_url`
  scripts mean it should render the real frontend (whole view, or a single card in a host page). Documented
  limitation.

## 7. Testing strategy

- `:dashboard-core` unit tests with JSON fixtures.
  - Real dumps live in a **gitignored** folder (`tools/ha-probe/out/`).
  - Committed fixtures must be anonymized: replace names, areas and ids.
- **Differential testing (golden):** drive the real frontend with Playwright and dump strategy output to JSON.
  - Call `customElements.get('home-…-strategy').generate(cfg, hass)` and dump `hass` alongside it.
  - The Kotlin port must produce identical JSON for the same snapshot.
  - Later: a Node harness for the pure TS functions (state display, conditions, entity_filter).
- WS: fake WebSocket tests for diff application, reconnection and resubscription, using Turbine.
- Room: cached load renders with `connection = Cached`.

## 8. M1 implementation sequence

1. **Foundation:** add the `:dashboard-core` and `:dashboard` modules, and the generic WS patch in `:common`.
   `DashboardActivity` reuses `ServerManager`.
2. **Vertical slice:**
   - Fetch panels, dashboards, config and registries.
   - Model the `sections` view with heading + tile cards from a stored config.
   - Live `subscribe_entities` updates.
3. **Home strategy, part 1:**
   - Port `home-area-view-strategy` and `computeAreaTileCardConfig`; area views are headings + tiles, i.e. exactly
     the P0 cards.
   - Overview = a list of areas linking to them. This gives the target server a real native dashboard early.
4. **Home strategy, part 2:** the overview view (area cards, summaries, common-controls), then media-players and
   other-devices.
5. **Derivations:** state display with translations, icons, colours, names. Port with fixtures.
6. **Cards:** the remaining P0/P1 list, and conditions/visibility.
7. **Masonry and panel views**, stacks, grid card.
8. **Actions** (gated): toggle, perform-action, navigate, more-info (basic sheet).
9. **Offline:** Room cache of raw config, states and registries; cached indicator; resubscribe on reconnect.
10. **WebView fallback** for unsupported cards and views.
11. Hardening, docs of unsupported features, M1 acceptance pass.

## 9. Risks and open questions

1. **`home` strategy drift.** It's large, changes fast, and is the default dashboard. Pin the port to the frontend
   version and gate on `ha_version`. Supporting several frontend versions is an open question.
2. **Server-side visibility on newer versions** (`subscribe_condition`). The condition evaluator needs a server path
   when the server supports it.
3. **Translations.** Many labels come from the frontend bundle, not the backend, so `src/translations/*.json` must be
   shipped per version.
4. **Layout fidelity.** Sections grid maths, masonry card sizes and `view_columns` media queries need careful porting.
5. **Custom cards and HACS** need a WebView, so fidelity and performance are limited.
6. **Internal APIs:** `usage_prediction/*`, `frontend/get_system_data`, `list_for_display` compact keys and strategy
   output shapes are not public contracts.
7. **The `:common` WS patch** has to be carried on every upstream merge until (if ever) it's accepted upstream.
8. **Heavy-data cards** (history, statistics, energy, camera): deferred, with WebView fallback in the meantime.

## 10. Native dashboards inside the app: navigation and the web hand-off

Goal: dashboards are native (instant, offline); every other page stays the web frontend in the WebView.

**Pieces that already exist**

- The frontend's external-app mode: with `hasSidebar: true` in the app's `config/get` answer it hides its own sidebar,
  and its menu button sends `sidebar/show` to the app (the iOS app works this way).
- The `navigate` command (HA 2025.6+) moves the frontend to a path; `homeassistant://navigate/<path>` deep links
  (and `?more-info-entity-id=`) reach the frontend through `LinkHandler` → `LaunchActivity` → `FrontendRoute`.
- `HAWebViewClient.doUpdateVisitedHistory` sees every route change of the frontend (it changes routes without
  reloading).

**Design**

1. One router decides, per path, native or web: a path whose panel is a dashboard the native renderer supports
   (`home`, `lovelace`, storage dashboards, and their views) is native; anything else is the WebView at that path.
2. Native dashboards are a destination of the app's nav graph (`NativeDashboardRoute` next to `FrontendRoute`), and
   the start destination when the feature is on.
3. The native screen has the navigation drawer: a port of `ha-sidebar` (`dashboard-core` `navigation/Sidebar.kt`,
   golden-tested). Dashboards switch natively; other panels, Settings and the profile open the WebView at their path.
4. In the WebView the frontend hides its sidebar (`hasSidebar`); its menu button (`sidebar/show`) returns to the
   native screen with the drawer open. A route change to a dashboard path (a link, the frontend's back) returns to
   the native dashboard too.
5. Native actions that navigate outside the dashboard (`/config/...`) and the more-info "More details" open the
   WebView at that path.
6. The native dashboards and the frontend share one destination (`NativeDashboardShell`), so one WebView is kept
   between web visits. It is created lazily, on the first page opened in it (never at launch, no preloading), and
   then kept loaded but hidden while the native dashboards show. Later pages open in it with the frontend's
   `navigate` command after clearing its history, so they don't load the frontend again (servers before 2025.6
   load it again). System back goes through the WebView history first, then back to the native dashboards. The
   hidden layer is laid out but not placed (not drawn, no touches), and its back handlers sit on a disabled child
   `NavigationEventDispatcher`. Switching the active server drops the frontend; the next web page creates a new one.
   The frontend destination (`FrontendRoute`) is still used for links to a server that isn't the active one, and
   with the flag off.

Everything is behind `WIPFeature.USE_NATIVE_DASHBOARD` (debug builds) while it settles; with it off the app is
unchanged.

**Phases**: A) native start screen with the drawer, web for other panels, `hasSidebar` and `sidebar/show`;
B) route changes inside the WebView back to native, native `/...` navigation to the WebView; C) deep links start
native, top-level back behaviour, server switching. All three are in, and so is the WebView kept alive across web
visits. Still to do: the offline behaviour. Possible later: pausing the hidden frontend (it keeps its JavaScript and
connection running while the native dashboards show), and preloading it when the app is idle.

