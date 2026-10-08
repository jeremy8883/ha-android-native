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
- **UI-heavy work** (visual design, card polish) is delegated to **Astra**. Keep logic and data in pure, testable Kotlin so the UI layer can be handed off cleanly.

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
- [ ] `:dashboard` Android module wired into the build, with minimal edits to upstream files
- [ ] Reuse existing auth / ServerManager / WebSocket
- [ ] Entry point / navigation into native dashboard

### Step 3 — Vertical slice (connect → load → render → live)
- [ ] Port the `home` dashboard strategy (pure fn: states + registries → config) — needed when no stored dashboard exists
- [ ] List dashboards (`lovelace/dashboards/list`)
- [ ] Load dashboard config (`lovelace/config`)
- [ ] Parse config, preserving unknown fields
- [ ] Render view structure plus one card type
- [ ] Live entity updates (`subscribe_entities`)
- [ ] React to `lovelace_updated`

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
- [ ] Persist last dashboard config (Room)
- [ ] Persist last known entity states
- [ ] Render from cache with a visible "cached" indicator
- [ ] Explicit connection status, resubscribe on reconnect
- [ ] Read-only offline: commands disabled while disconnected

### Step 7 — Tests
- [ ] JSON fixtures (real dashboards) and deserialization
- [ ] Config interpretation and layout
- [ ] Derived entity state
- [ ] Conditions / visibility
- [ ] Supported features
- [ ] WS state updates and reconnection
- [ ] Cached loading
- [ ] Unknown / unsupported configs

## Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-08 | Public GitHub fork, with work on the `native-dashboard` branch | GitHub doesn't allow private forks. A real fork keeps upstream sync easy. |
| 2026-10-08 | Port behaviour from the frontend release matching the target server (20260624.6), not frontend main | Behaviour must match what the server actually ships |
| 2026-10-08 | Two new modules: `:dashboard-core` (pure JVM) and `:dashboard` (Android, depends on `:common`) | Pure logic stays fast to test and diff-test; minimal upstream file edits (see docs/architecture.md §2) |
| 2026-10-08 | The `home` strategy (the `/home` panel) is on the critical path for M1 | With no stored dashboard, `lovelace/config` returns `config_not_found` and the frontend shows `/home`, generated client-side by `strategies/home/*` |

## Open questions

- How do we support several frontend versions at once? Gate on `ha_version`, and use per-version fixtures?
- Differential testing: can the TS strategies run headless in Node against fixture `hass` data?

## Build notes

- Use JDK 21; CI does, and newer JDKs may fail.
- Before any `:app` build: `cp .github/mock-google-services.json app/google-services.json`. Build with `./gradlew :app:assembleMinimalDebug`.
- `./gradlew :common:testDebugUnitTest` passes cold in about 7.5 minutes; NDK 29 installs automatically for `:microwakeword`.
- Dependency lockfiles are global, so avoid adding new libraries to `:app`/`:automotive` (merge conflicts).

## Handover notes

### 2026-10-08: session 1 (Opus)
- Done: fork, investigation docs, `docs/architecture.md`, read-only probe tool.
- Next: Step 2 foundation (modules + generic WS patch in `:common`), then the vertical slice per architecture.md §8.
- Gotchas: the target frontend is 20260624.6, not main. The default dashboard is the `/home` panel (generated), not a stored config.
