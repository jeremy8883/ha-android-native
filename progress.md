# Native Dashboard — Progress & Handover

Living checklist for the native Jetpack Compose Lovelace renderer. **Read this first when picking up work.**
Update it in the same commit as the work it describes.

## Context

- **Goal:** native Compose/Material 3 renderer for *existing* Home Assistant dashboards. No new dashboard format: HA stays the source of truth.
- **Repo:** fork of `home-assistant/android` → `jeremy8883/ha-android-native`.
  - `main` mirrors upstream (sync via GitHub "Sync fork" or `git fetch upstream && git merge upstream/main`).
  - Work happens on `native-dashboard`. Merge upstream `main` into it regularly.
  - `upstream` push URL is disabled on purpose. Upstream's [AI policy](AI_POLICY.md) forbids autonomous PRs/issues/comments, so **never** open anything against `home-assistant/*`.
- **Reference clones** (shallow, read-only) live at `~/projects/ha-refs/`: `frontend` (main), `frontend-20260624.6` (the **primary port reference**, matching HA 2026.7.x), and `core`.
- **Test server:** `tools/test-ha/up.sh` starts HA 2026.7.4 in podman at `http://localhost:8124` (login dev/dev). It runs `demo` plus seeded floors, areas, a stored `dashboard-test` dashboard and home favourites. Reads and writes are both fine here. See `tools/test-ha/README.md`.
- **Probe:** `tools/ha-probe/ha_ro.py` targets the test server by default; pass `--allow-write` to send writes. `--live` targets a real server and is strictly read-only. Output goes to `tools/ha-probe/out/` (gitignored). Never commit dumps from a real server; anonymize them first.
- **Investigation docs:** `docs/investigation/`.
- **UI-heavy work** (visual design, card polish) is delegated to **Astra**; see **`docs/ui-handover.md`** for ownership, the card-model contract and how to run it. Logic stays in pure, testable Kotlin in `:dashboard-core`.

## Upstream versions tracked

| Repo | Commit | Date |
|---|---|---|
| home-assistant/android | `eaa2122730ace117bbe7181361b2a86f12be9aac` | 2026-10-06 |
| home-assistant/frontend | `20260624.6` (`2dabc28e`), the port reference | ships with HA 2026.7.4 |
| home-assistant/frontend (main) | `bc21992178b1d11a2e3b5f652760c64bb633a39c` | 2026-10-07 |
| home-assistant/core | `767937befd80fae899a4718b65c7b7626024466a` | 2026-10-08 |

## Milestone 1 — Native dashboard rendering

### Step 0 — Setup
- [x] Fork repo, configure `origin`/`upstream` remotes, `native-dashboard` branch
- [x] Clone frontend + core references
- [x] Create progress.md

### Step 1 — Repository investigation
- [x] Frontend Lovelace investigation → `docs/investigation/frontend-lovelace.md`
- [x] Android app reuse investigation → `docs/investigation/android-reuse.md`
- [x] Core backend / WS protocol investigation → `docs/investigation/core-backend.md`
- [x] Architecture overview + recommendations → `docs/architecture.md`
  - [x] Fork vs separate decision
  - [x] APIs and data structures for dashboard rendering
  - [x] Kotlin module structure
  - [x] Card and layout inventory
  - [x] M1 implementation sequence
  - [x] Risks and open questions

### Step 2 — Android foundation
- [x] Generic raw WS API in `:common` (`sendRawMessage`, `subscribeRaw`, opt-in `rawEvents`). **Carried upstream-file patch:** WebSocketCore/Impl, WebSocketRequest, WebSocketRepository/Impl
- [x] `:dashboard-core` (pure JVM): config wrappers that preserve unknown keys; `applyEntityEvent` (port of js-websocket `processEvent`)
- [x] Local test HA container plus fixture capture (`tools/test-ha/`)
- [x] `:dashboard-core` derivations: `isActive` (port of `stateActive`), basic `tileModel`
- [x] `:dashboard` Android module wired into the build. Upstream edits: `settings.gradle.kts`, plus one line in `app/build.gradle.kts`
- [x] Reuse existing auth / ServerManager / WebSocket (`DashboardRepository`)
- [x] Entry point: `DashboardActivity`; debug builds get a separate "Native dashboard" launcher icon

### Step 3 — Vertical slice (connect → load → render → live)
- [ ] Port the `home` dashboard strategy (pure fn: states + registries → config), needed when no stored dashboard exists
  - [x] Dashboard level (`homeDashboard`) and area views (`homeAreaView`), plus helpers. **Golden-tested** against the real frontend output (14/14, admin and non-admin)
  - [x] Wired into the app: the default dashboard falls back to the generated home dashboard on `config_not_found`
  - [x] Overview view (`home-overview`) and the `common-controls` section strategy, golden-tested (20/20 with section and full expansion)
  - [x] `home-media-players`, `home-other-devices` views, golden-tested (24/24 strategy goldens)
  - [ ] Native rendering of the cards these views use. Basic heading and area cards exist; still to do: more entity row types, vertical tiles (`vertical: true`, e.g. the overview's Devices tile on tablets)
    - [x] tile features used by the home strategy: light-brightness (slider), cover-open-close and lock-commands (buttons), fan-speed (speed segments or slider), alarm-modes (mode segments), target-temperature (+/- with 1 s debounce, single or range). Models in `feature/TileFeatures.kt`; protected lock/alarm calls use the entity's default code or ask for one (`CodeDialog`). Verified on the test instance (brightness, fan speed, alarm arming with code)
    - [x] Sections view sidebar (the home overview's Summaries on large screens): a column of its own beside the content, or Home/Summaries tabs on narrow (≤ 870dp) screens, following `hui-sections-view`; checked at 411, 800 and 1066dp
    - [x] conditional card: hidden (taking no space) while its conditions fail, otherwise its card; inner cards get their templates and snapshots
    - [x] picture-entity: camera snapshots signed with `auth/sign_path` and refreshed every 10 s like `hui-image`, configured/image/person pictures; media artwork. Images load through the app's Coil image loader (coil-compose added) against the server's current URL (`LocalServerUrl`). The test dashboard has a Cameras section
    - [x] shortcut card (label and icon from area targets, URLs, Assist; panel and service names not resolved yet)
    - [x] entities (toggle, button, input button, scene, script and lock rows; text rows otherwise), media-control (title, description, upstream's controls; no artwork or progress yet) and empty-state cards. Fixed translation references (`[%key:...%]`) in the extracted bundle
    - [x] markdown: templates rendered by the server (`render_template`, variables `config` and `user`) and kept live, shown with multiplatform-markdown-renderer-m3 (new dependency); `show_empty: false` hiding. View header: header card and view entity badges (`layout/ViewHeader.kt`)
    - [x] heading: badges (entity and button) with their visibility conditions, actions and colours; chevron when actionable. Colours port `stateColorCss`/`computeCssColor` as `DisplayColor`, resolved against the frontend's default palette (`ui/theme/FrontendColors.kt`, generated by `tools/theme/extract_colors.py`)
    - [x] repairs, updates, discovered-devices: `InfoTileModel`s with live repairs (`repairs/list_issues`, refetched on `repairs_issue_registry_updated`) and discovery flows (`config_entries/flow/subscribe`); self-hiding (admin only, `hide_empty`) takes them out of the layout. Golden-tested against the frontend's cards (admin and non-admin)
    - [x] home-summary: `HomeSummaryModel` golden-tested against `hui-home-summary-card` (outputs/cards.json); rendered with the tile layout. Energy stays "loading" until energy statistics are fetched
    - [x] UI polish for the initial tile, heading and compact area renderers: shared HA-token card surface, active/unavailable treatment, and safe dynamic MDI icons
  - [x] View navigation like upstream: tabs only for non-subviews, `navigate` actions open subviews, back arrow plus system back
  - [x] Weather attribute units (`getWeatherUnit`); every tile of the generated dashboard is golden-tested for name and secondary line (outputs/entity-display.json `tiles`)
  - [x] Entity icons (`ha-state-icon`: registry, attribute, server icon translations, built-in state icons, domain fallback), entity names (`formatEntityName`), formatted states (`formatEntityState`, attribute values) and the tile secondary line (`state-display`, incl. relative times), golden-tested per entity against the real frontend (outputs/entity-display.json)
  - [x] Sections layout: card sizes from each card type's `getGridOptions` plus `grid_options`/`layout_options`, CSS-grid auto-placement in each section's 12×span grid, sections placed in the view's columns (hidden sections take no space). Rendered by `ui/DashboardGrid.kt`; fixed-row cards get a 56dp-row minimum height. Not yet: the overview sidebar, `dense_section_placement`, masonry columns, a golden test against browser placement
  - [x] More-info: a native quick view (`MoreInfoModel`: name and area/device, state and when it changed, toggle, the domain's main control reusing tile features, media controls, attributes filtered like `ha-attributes`) in a bottom sheet, with "More details" opening upstream's full dialog through the app's `homeassistant://navigate?more-info-entity-id=` deep link
  - [x] Actions: tap/hold/double-tap (and the tile icon) resolve through a port of `handleAction` (`action/`): toggle (turnOnOffEntity), perform-action/call-service via `call_service`, navigate (views of this dashboard), url, confirmation dialogs with exemptions, upstream's failure toasts. Assist, fire-dom-event and navigation outside the dashboard show "not available yet" for now. Verified toggling on the test instance
  - [x] Visibility conditions: all types (state, numeric_state, and/or/not, user, location, time, screen, view_columns, legacy), with upstream's tests ported. Section and card visibility in rendering; `view_columns` uses upstream's sections column formula
- [x] List dashboards (`lovelace/dashboards/list`)
- [x] Navigation sidebar logic (`navigation/Sidebar.kt`): panels, default panel and the user's order/hidden panels like `ha-sidebar`, golden-tested against the real sidebar (admin with a custom order, non-admin default). Phase A of the app integration is in (docs/architecture.md section 10), behind `WIPFeature.USE_NATIVE_DASHBOARD` (debug builds): the app starts on the native dashboards (`NativeDashboardRoute` in `:app` `nativedashboard/`), the native drawer opens other panels, Settings and the profile in the web frontend, which hides its sidebar (`hasSidebar` in `config/get`) and whose menu button (`sidebar/show`) brings back the native drawer. Native `navigate` actions to `/...` and dashboard paths go through `DashboardViewModel.onOpenPath`. Unit tests force the flag off (`TestStateResetPlatformListener`) except the new ones. Phase B: route changes inside the frontend (links, its own back, `/` redirects) to a dashboard path hand over to the native dashboards (`NativeDashboardHandOff`, `NativeDashboardPaths`; `?edit=1` stays web for the editor). Phase C: deep links to dashboard paths and `more-info-entity-id` start native (the native screen sits at the bottom and opens other paths in the frontend on top; links for a non-active server stay web), back from another dashboard returns to the default one, and a Switch server drawer entry (several servers) activates a server and restarts the native screen for it. One WebView is now kept between web visits (`NativeDashboardShell`): the native dashboards and the frontend share the destination, the frontend is created on the first web page (never at launch) and kept hidden while native shows, and later pages open in it through the `navigate` command (`FrontendViewModel.openPath`) instead of loading it again. Native server switching now takes effect (`NativeDashboardServers`; activating a server doesn't change `serversFlow`, so it was never picked up). The hidden frontend is paused (its lifecycle capped at CREATED, so the WebView and its JavaScript timers stop) and a page opened in it waits for it to resume. The native drawer wraps both layers, so the frontend's menu button opens it over the web page (`NativeDashboardWeb`, `ScreenLayer`); it is sized like Material's phone drawer (screen less 56dp, at most 320dp). "More details" opens the frontend's more-info dialog over the current dashboard and returns to native when it closes: the frontend marks its URL on `dialog-closed`, since the dialog's script-pushed history entry is skipped by `WebView.canGoBack`. The frontend's own `mdi:` logos (`music-assistant`, `home-assistant`, `esphome`, `matter`) are generated by `tools/icons/extract_custom_icons.py`. Still open: offline behaviour
- [x] Load dashboard config (`lovelace/config`)
- [x] Parse config, preserving unknown fields
- [x] Render view structure plus one card type (tile); other types show a placeholder
- [x] Live entity updates (`subscribe_entities`)
- [x] React to `lovelace_updated`
- [x] Slice verified on a physical device (2026-10-08) against the test container via `adb reverse tcp:8124 tcp:8124`: tiles render, a light toggled on the server updates within ~2 s, and a `lovelace/config/save` re-renders within ~3 s

### Step 4 — Card renderers
- [ ] Entities
- [ ] Tile
- [ ] Button
- [ ] Sensor
- [ ] Gauge
- [ ] Glance
- [ ] Light
- [ ] Thermostat
- [ ] Markdown
- [ ] Conditional
- [ ] Unsupported-card fallback (placeholder, then WebView investigation)
- [ ] Layouts: masonry, sections, panel, sidebar, stacks/grid

### Step 5 — Reactive state and actions
- [ ] Derived display state (state display, icons, colors)
- [ ] Actions: toggle, perform-action, navigate, more-info (basic)
- [ ] Unavailable / unknown / disconnected handling

### Step 6 — Offline foundations
- [x] Persist last dashboard config (Room, `data/cache/`)
- [x] Persist last known entity states (a row per entity, written when changed)
- [x] Render from cache with a visible "cached" indicator (offline bar with "Updated …"; refresh bar while loading again)
- [x] Explicit connection status, resubscribe on reconnect (`connectionStatus()` in `:common`; data reloads on reconnection, the next states snapshot replaces the old states)
- [x] Failures are never shown as empty data: `Loadable`/`LoadError`, last good value kept, retries, error screen with Retry, refresh bar, offline bar (docs/architecture.md section 4)
- [x] Action failures like upstream: translated exception messages, 10s message, haptic, toggles flip back after 2s without a state update
- [x] Offline, controls stay enabled and a failed action says so (decided 2026-10-09, instead of disabling them)

### The dashboard app (`:dashboard-app`, `net.jeremycasey.homeassistantnative`)
Decided 2026-10-09: the native dashboards are an app of their own, installed alongside the companion app (which keeps notifications, location and setup), also meant for family members who only control devices. It lives in this fork and builds on `:dashboard` and `:common`; the companion app's own module (`:app`) goes back to upstream.
- [x] Module, own app id, Hilt bindings `:common` needs (`AppModule`), no WebView: HTTP calls opt out of WebView cookies (`@WebViewCookies` in `:common`), so starting the app doesn't load the WebView
- [x] Native login through Home Assistant's login API (`login/`): server discovery (copy of the companion app's `HomeAssistantSearcher`) or an address, the server's login forms (password, two-factor, trusted networks), then the code exchanged as the companion app does; no mobile device registration
- [x] Drawer Settings entry: servers (switch, add, log out, which revokes the session), server switcher sheet
- [x] Pages the dashboards don't show open in the companion app (`homeassistant://navigate`), or the browser without it
- [x] Revert the native dashboard integration in `:app` (`bd0c95983`; `app/` is as at the branch point); the web layer, hand-off and standalone debug activity are gone from `:dashboard` too
- [x] Setting "Open other pages in the Home Assistant app" (off by default): off, the drawer lists only dashboards, More details is hidden, and links to other pages say they aren't available; on, they open in the companion app or the browser
- [x] Server list and switcher look like the companion app's chooser (avatar or initials, check badge on the active server; `ServerUserAvatarUseCase` copied)
- [x] Images load through the app's HTTP client (Coil set up as the companion app's `HomeAssistantApplication` does)
- [ ] Port the summary panels the overview links to (`/light`, `/climate`, `/security`, `/maintenance`; frontend `panels/<name>/strategies/*-view-strategy.ts`, ~270 lines each) so they open natively; Energy stays a later, larger piece
- [ ] Dashboards generated by strategies not ported yet (such as Map) show "not supported natively yet"; leave them out of the drawer, or port them
- [ ] Edit a server's name and addresses (internal URL, home networks) in Settings
- [ ] Client certificates (mTLS) in the native login: pick one with `KeyChain` when the server asks
- [ ] Sign in with the browser for servers behind a web sign-in page (Cloudflare Access, Authelia...); ideally detect when it's needed and offer it automatically
- [ ] WebView fallback for cards not drawn natively yet, created only when a dashboard has one
- [ ] Widgets and notification-drawer actions, ported from the companion app

### Pages outside the dashboards
Every place the dashboards lead out of the app. With "Open other pages in the Home Assistant app" off (the default), cards that only lead there are hidden, headings keep their title without the link, the drawer lists only dashboards, and More details is hidden. Tick an item off when it opens natively, or decide it belongs to the companion app.

Overview summaries (generated home dashboard):
- [x] Lights → `/light` (native; port of `panels/light/strategies/light-view-strategy.ts`)
- [x] Climate → `/climate` (native; `panels/climate/strategies/climate-view-strategy.ts`; the `trend-graph` tile feature is not native yet and leaves blank space)
- [x] Security → `/security` (native; `panels/security/strategies/security-view-strategy.ts`; the Activity sidebar's `logbook` card is not native yet)
- [x] Maintenance → `/maintenance` (native; `panels/maintenance/strategies/maintenance-view-strategy.ts`)
- [ ] Energy → `/energy` (opens natively; the cards are being ported, see Energy below)
- [x] Media players → `media-players` view (native)
- [x] Weather → more-info (native)

Overview admin cards (companion app only; hidden when off):
- [x] Repairs → `/config/repairs`
- [x] Updates → `/config/updates`
- [ ] Discovered devices → upstream opens the add-integration dialog; the native card has no tap yet

Room and device views (generated):
- [x] Section headings Lights / Climate / Security → the summary pages above (native; back returns to the room)
- [x] Media players heading → `media-players` view (native)
- [ ] Device headings → `/config/devices/device/<id>` (companion app; link hidden when off)
- [ ] Scenes heading → `/config/scene/dashboard`, Automations heading → `/config/automation/dashboard` (companion app; link hidden when off)
- [x] "Other devices" → `/home/other-devices` (native)

Anywhere:
- [ ] Shortcuts and `navigate` actions in user dashboards to other paths (each depends on its target)
- [ ] More details → the frontend's full more-info dialog (history, logbook, settings, related); grow the native more-info sheet instead

Drawer panels (shown only when the setting is on):
- [ ] Map (a dashboard generated by the `map` strategy; shows "not supported natively yet")
- [ ] History, Logbook, Calendar, To-do lists, Media browser, Energy
- [ ] Settings (`/config`), Profile, add-on and custom panels (such as Music Assistant): companion app

### Energy
The energy panel (`/energy`) as frontend 20260624.6 builds it, in phases.
- [x] Test instance: `tools/test-ha/seed_energy.py` imports 60 days of statistics for grid (with cost and compensation), solar, battery, gas, water and devices (one included in another), with live power and flow template sensors; the capture records the energy panel
- [x] Dashboard and view strategies (`strategy/energy/`, golden-tested): Summary, Electricity, Gas, Water, Now; hidden cards from the `energy` system data
- [x] Periods (`EnergyPeriod`: the selector's ranges, previous/next, now, comparison) and what `getEnergyData` fetches for one (`EnergyFetch`, golden-tested against the frontend's requests for six periods)
- [x] `EnergyRepository` (today's data cached, other periods kept while the screen lives, hourly refresh at :20) and the view model's collections, read by cards from `hass.energy`
- [x] Date selection footer: previous/next, ranges menu, now, compare (no free date range picker yet)
- [x] Energy distribution card: `EnergyDistributionModel` (sums and consumption split ported from `data/energy.ts`) golden-tested against the frontend card's amounts, home ring and flow speeds for six periods; drawn with the frontend's SVG geometry and moving dots
- [x] Energy usage graph: series ported from `hui-energy-usage-graph-card` (incl. compare, grid-to-battery split, colour shading per source) golden-tested against the frontend chart's series; drawn by `EnergyBarChartView` (Canvas: stacked bars above/below zero, compare stacks side by side, rounded caps, round value ticks, time axis, tap tooltip, toggling legend). Vico was considered but can't stack and group bars together, so the energy bar charts are drawn directly
- [x] Energy sources table: `EnergySourcesTableModel` golden-tested against the frontend table's rows for every period and five configs (all, totals only, electricity, gas, water); scrolls sideways when the compared columns don't fit, rows open more-info
- [x] Energy gauges (self-sufficiency, grid neutrality, self-consumed solar with the battery tracked last in first out, low-carbon): golden-tested against the frontend's gauge cards for every period; drawn like `ha-gauge` (arc or levels with needle, fitted value text), info behind the info icon
- [x] Gas, water and solar graphs (`EnergySourceGraphModel`), compare banner (`EnergyCompareModel`: switch to previous year/period, stop comparing) and grid balance (`EnergyGridBalanceModel`), golden-tested against the frontend's cards for every period
- [ ] Solar forecast lines on the solar graph (`energy/solar_forecast`; the test instance has no forecast integration)
- [x] Device cards: the detail graph (devices by period without their included devices, untracked and over-reported consumption) and the devices graph (bars or donut, switched from the header), golden-tested against the frontend's cards for every period; device colours from the graph palette (`--color-N`, now extracted)
- [ ] Devices graph: remember the chosen bar/donut mode across launches (the frontend keeps it in local storage)
- [x] Energy sankey: nodes and flows (by floor and area) and the chart's layout input golden-tested against the frontend's; laid out like ECharts' sankey with `layoutIterations: 0` (`SankeyLayout`) and drawn with gradient flows, vertical on phones like the frontend; theme colours now include the core palette and `var()` fallbacks
- [ ] Test data: give the device statistics entities with areas, so the sankey's floor and area grouping is golden-tested too
- [x] Water sankey (`waterSankey`, sharing the devices part with the energy sankey), golden-tested
- [x] Now tab: power sankey and water flow sankey (`RateSankeyModel`, from the live states, sharing the energy sankey's device grouping), power/gas/water total badges (`layout/EnergyBadges.kt`), all golden-tested; the capture adds scenarios with states overridden in the page only (discharging, grid charging, small consumers grouped as "Other", water flowing)
- [x] Power sources graph: series golden-tested (`PowerSourcesGraphModel`, today's graph ends with the current states); drawn by `PowerLineChart` (Canvas: areas stacked above/below zero with ECharts' smoothing ported from `poly.ts`, dashed use line, tap tooltip, toggling legend). Vico 3.3 was evaluated but its line layer can't stack areas
- [x] Theme colours: the extractor also reads `semantic.globals.ts` (`--ha-color-text-*`...), so `primary-text-color` and friends resolve
- [x] Home dashboard "Today's energy" summary: its own `energy_home_dashboard` collection, always today, golden-tested against the frontend's tile; says it couldn't load rather than showing nothing

### Later — Startup time
- [ ] Cold launch straight to the dashboard: the dashboard app has no splash wait (it starts from the cache); measure it on a release-like build on a real device
- [x] Don't start Chromium at launch (dashboard app: no WebView cookies)
- [ ] Baseline profile (none in the project yet)

### Step 7 — Tests
- [ ] JSON fixtures (real dashboards) and deserialization
- [ ] Config interpretation and layout
- [ ] Derived entity state
- [x] Conditions / visibility (`ConditionsTest`, `CardGroupVisibilityTest`)
- [ ] Supported features
- [ ] WS state updates and reconnection
- [x] Cached loading (`LoadedDataTest`, `KeptDataTest`)
- [ ] Unknown / unsupported configs

## Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-08 | Public GitHub fork, with work on the `native-dashboard` branch | GitHub doesn't allow private forks. A real fork keeps upstream sync easy. |
| 2026-10-08 | Port behaviour from the frontend release matching the target server (20260624.6), not frontend main | Behaviour must match what the server actually ships |
| 2026-10-08 | Two new modules: `:dashboard-core` (pure JVM) and `:dashboard` (Android, depends on `:common`) | Pure logic stays fast to test and diff-test; minimal upstream file edits (see docs/architecture.md §2) |
| 2026-10-08 | The `home` strategy (the `/home` panel) is on the critical path for M1 | With no stored dashboard, `lovelace/config` returns `config_not_found` and the frontend shows `/home`, generated client-side by `strategies/home/*` |

## Golden tests

- `tools/golden/capture.mjs` (Playwright) drives the real frontend on the test instance and records raw WS inputs plus strategy outputs into `dashboard-core/src/test/resources/fixtures/home/<variant>/`. `HomeStrategyGoldenTest` feeds the same raw data to the Kotlin port and compares JSON. Re-capture after a frontend version bump; see `tools/golden/README.md`.
- `usage_prediction/common_control` depends on history and time of day, so tests must use the recorded `ws/strategy-calls.json`.

## Open questions

- Known deviations after the empty-data audit: a refused `usage_prediction/common_control` leaves the common controls section out (upstream fails that section); a template whose subscription can't be made shows its raw text until it renders, as upstream; an `subscribe_entities` addition without `s` still gets an empty state.
- `:common` shares identical subscriptions (`findSubscription`) without replaying their snapshot, so a second collector of the same `subscribe_entities` would miss it. The dashboards subscribe once per screen, so it doesn't arise today.

- How do we support several frontend versions at once? Gate on `ha_version`, and use per-version fixtures?
- Differential testing: can the TS strategies run headless in Node against fixture `hass` data?

## Build notes

- Use JDK 21; CI does, and newer JDKs may fail.
- Before any `:app` build: `cp .github/mock-google-services.json app/google-services.json`. Build with `./gradlew :app:assembleMinimalDebug`.
- `./gradlew :common:testDebugUnitTest` passes cold in about 7.5 minutes; NDK 29 installs automatically for `:microwakeword`.
- Dependency lockfiles are global, so avoid adding new libraries to `:app`/`:automotive` (merge conflicts).

## Handover notes

### 2026-10-09: detekt clean (Opus)
- `detektMain` passes for `:dashboard-core`, `:dashboard` and `:app` (was 136 findings), with no suppressions or config changes; commit gate is now ktlint + detekt + tests.
- `:dashboard` data is split by concern: `DashboardRepository` (configs, registries, server info, translations), `LiveDataRepository` (states, live collections, templates, cameras, connection), `ServerActionsRepository` (service calls), `ActiveServerRepository` (active server, panels), sharing `ServerSessions` (view-model scoped, carries the user's retries). `DashboardActions` runs card actions for the view model.
- `FrontendCallbacks` is an interface (`FrontendLinkCallbacks` + `FrontendWindowCallbacks`), implemented in `HANavHost`; `NativeDashboardShell` keeps its state in `ShellState`.
- Core files were split by topic (formatting, tile features, home strategy parts); behaviour unchanged (golden tests).

### 2026-10-09: failures are not empty (Opus)
- The "all rooms empty" bug: after the app was in the background for more than 5s, the dashboard's flows restarted and fetched the registries again, often before the connection was back; the failed requests became empty lists. Data now goes through `Loadable` and `KeptData`, and keeps its last value per server (`LoadedData`).
- `DashboardRepository` returns `Flow<Loadable<T>>` for every piece of data and `Fetched<T>` for one-off requests; `dashboards()` (unused) is gone. `DashboardViewModel.status` carries refreshing, offline and refresh errors for the screen; `sidebar` is a `Loadable` and the drawer shows its loading and failure.
- Then: action failures like upstream (`showActionFailed`, `EntityToggle`), and the dashboard cache behind `LoadedData` (Room, `data/cache/`), cleared when a server is removed. Back now closes the drawer (the sheet needs the drawer state).

### 2026-10-08: entity display (Opus)
- `TileModel` now carries display-ready text: `name` (formatEntityName), `state` (the full secondary line, unit included; `unit` is gone), `icon` (always resolved for existing entities). `HassSnapshot.tileModel(card, now)` needs the current time for relative timestamps; cards get it as `State<ZonedDateTime?>`.
- Locale formatting sits behind `display/DisplayFormats`; `JdkDisplayFormats` is English-accurate (golden-tested). Relative times are English only for now; the app always uses the bundled `en` strings and the server's `en` translations. Other languages need an Android implementation (ICU `RelativeDateTimeFormatter`) and translated bundles.
- Translations: `Localize(key, args)` formats ICU messages (plural/select/`#`, English plural rules) via `formatIcuMessage`. The bundle now also has `state`, `ui.card` and `ui.panel.lovelace.components`.
- Test instance: areas now have temperature/humidity sensors (bootstrap `AREA_SENSORS`); goldens re-captured.
- Sections grid is in (`layout/ViewLayout.kt` + `ui/DashboardGrid.kt`). Tiles now render single-line, start-aligned text (HATextStyle.Body is centred by default, which broke narrow tiles); Astra may want to revisit.
- Cards get `onGesture(config, gesture)`; `cardActions(card)` says which gestures an element reacts to. `DashboardGrid` wraps every item in its own Box (a card drawing nothing crashed the measure otherwise).
- Feature controls are first versions: Astra may want to restyle them (the enabled lock button blends into an inactive tile).
- Not ported yet: `number_format: none`/explicit 12/24h preferences, the user's frontend locale settings (we use language formatting and the device time zone).

### 2026-10-08: Astra UI track
- Polished the initial native tile, heading and compact area renderers and split them into focused files under `dashboard/ui/cards/`.
- Added a safe `mdi:*` name-to-vector renderer using the existing MDI Compose dependency. Unknown names render no icon rather than failing.
- Compared the generated Overview against the matching local web frontend and validated Overview, Test dashboard, area navigation and system back on `emulator-5554` in light and dark modes. The picker and view tabs now use explicit HA tokens instead of Material's default purple/dark colors. No StrictMode or runtime crashes were logged.
- Polished Opus's first feature controls: enabled command buttons now use a distinct HA tonal surface (so unlock no longer disappears into an inactive tile), disabled actions retain disabled tokens, and target temperature is one compact native pill instead of three disconnected circles. Verified the controls in light/dark mode and exercised temperature +/- on the test server, restoring the original value. Weather units remain a `:dashboard-core` display-model task.
- Kept derivation in `:dashboard-core`: UI continues to read tile/area display models from `derivedStateOf`; no dashboard data or ViewModel files changed.
- Verified with `:dashboard:ktlintFormat` and `:dashboard:compileDebugKotlin` on JDK 21.
- Next UI work: render the remaining generated-home card types as their core display models become available, then add isolated interaction and screenshot coverage for meaningful card states.

### 2026-10-08: session 1, later (Opus)
- Done: generic raw WS API in `:common`, `:dashboard-core`, `:dashboard` vertical slice (tile card plus placeholders), local test HA container. `:app:assembleMinimalDebug` builds.
- App onboarding on a device/emulator: `ANDROID_SERIAL=emulator-5554 python3 tools/test-ha/onboard_app.py` (after `adb reverse tcp:8124 tcp:8124`). It refuses to type credentials unless the app's login URL is `localhost:8124`, because LAN discovery can surface other servers.
- Verified on a physical device. The headless emulator (36.4.10) segfaults on this host, so use a USB device with `adb reverse tcp:8124 tcp:8124`, then onboard to `http://localhost:8124` (dev/dev). **Discovery also finds other servers on the LAN; always use "Enter address manually".** The debug app's package is `...minimal.debug`, which doesn't clash with the store app. Launch it with `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n io.homeassistant.companion.android.minimal.debug/io.homeassistant.companion.android.dashboard.ui.DashboardActivity`.
- Next: verify the slice; unit-test `DashboardRepository` with a fake WebSocket; then the home strategy (architecture.md §8 step 3).

### 2026-10-08: session 1 (Opus)
- Done: fork, investigation docs, `docs/architecture.md`, read-only probe tool.
- Next: Step 2 foundation (modules + generic WS patch in `:common`), then the vertical slice per architecture.md §8.
- Gotchas: the target frontend is 20260624.6, not main. The default dashboard is the `/home` panel (generated), not a stored config.
