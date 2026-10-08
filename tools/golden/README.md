# Golden fixtures for the `home` strategy

`capture.mjs` drives the real HA frontend (20260624.6, served by HA 2026.7.4) from the **local test
instance** in headless Chromium. It records what the `home` dashboard strategy reads and what it
generates, so the Kotlin port in `:dashboard-core` can be diff-tested against the shipped TS.

It only ever talks to `localhost`: it reads `TEST_HA_URL`/`TEST_HA_TOKEN` from `tools/test-ha/.env`,
refuses non-localhost URLs, and aborts every browser request that leaves localhost. It never looks
at `$HASS_SERVER`/`$HASS_TOKEN`.

## Re-run

```sh
tools/test-ha/up.sh                  # start the test instance (idempotent; seeds on first run)
cd tools/golden
npm install                          # Node 24 via mise; installs Playwright
npx playwright install chromium      # once per Playwright version
node capture.mjs                     # both variants; or --variant admin|nonadmin
```

Output goes to `dashboard-core/src/test/resources/fixtures/home/`:

- `test-instance/`: user `dev` (admin)
- `test-instance-nonadmin/`: user `golden-viewer` (a non-admin user that the script creates on the
  test instance on first run, password `golden-viewer`)

The script exits non-zero if a strategy threw or a cross-check failed. On a failed cross-check, it
writes both sides to `tools/golden/out/<variant>/` (gitignored).

## What each variant directory holds

| Path | Content |
|---|---|
| `inputs/*.json` | The frontend's processed `hass` view that the strategies read: `states` (timestamps and context frozen), `entities`, `devices`, `areas`, `floors`, `user`, `panels`, `config`, `language`, `locale`, `systemData`, `userData`. Also `home-system-data` (the panel's `_config`), `strategy-config` (the panel's `_strategyConfig`), `translations` (`ui.panel.lovelace.strategy.*`, `ui.panel.home.*`, `panel.*`), and `localize` (every `localize(key, args)` call the strategies made, with its result). |
| `ws/*.json` | Raw WS responses as `{request, result}` or `{request, error}`: `get_states`, the four registry lists, `auth/current_user`, `get_config`, `get_panels`, `frontend/get_system_data` (home, core), `frontend/get_user_data` (core), `usage_prediction/common_control`, `energy/get_prefs`, `manifest/get` (frontend), `frontend/get_icons` and `frontend/get_translations` (`entity_component` and `entity` categories). |
| `ws/strategy-calls.json` | The `callWS` calls the strategies themselves made during generation, with responses. These are the canned answers a Kotlin test should feed in. |
| `outputs/dashboard.json` | `home-dashboard-strategy` output |
| `outputs/views/<path>.json` | Each view with its view strategy resolved |
| `outputs/sections/<view>/<index>-<type>.json` | `{input, output}` for each strategy section (`common-controls`) |
| `outputs/entity-display.json` | Per entity, from the same snapshot: the icon `ha-state-icon` drew (`{fallback: true}` for the built-in domain icon), `formatEntityState`, the tile's secondary line (`state-display` with default content, as visible text), and `formatEntityName` for several `name` options. Also `now`, `locale` and `time_zone`, since relative times depend on them. |
| `inputs/state-translations.json` | Frontend bundle strings used by state display (`state.*`, `ui.common.*`, `ui.components.relative_time.*`). |
| `outputs/expanded.json` | The whole dashboard with every view and section strategy expanded, like `expandLovelaceConfigStrategies()` |
| `manifest.json` | HA and frontend versions, capture date, how `generate` was invoked, cross-check results, and the file list with sizes and hashes |

## How it works

1. A `hassTokens` entry is injected into localStorage before the page loads, so the frontend skips
   the login page. For admin it uses the long-lived token, with a far-future `expires` so the
   frontend never tries to refresh. For non-admin it uses a real access/refresh pair from the
   `/auth/login_flow` → `/auth/token` flow.
2. It opens `/home` and waits for `ha-panel-home._lovelace`.
3. It navigates in-app to every `/home/<view>`, so that each lazily loaded strategy chunk is defined.
   It records the frontend's own result for each view (`hui-view._config`) and section
   (`hui-section._config`).
4. It takes one `hass` snapshot (`document.querySelector("home-assistant").hass`), wraps it in a
   Proxy that logs `callWS` and `localize`, and calls each level directly, mirroring
   `get-strategy.ts`:
   `customElements.get(`${type}-${dashboard|view|section}-strategy`).generate(cleanLegacy(strategy), hass)`,
   merged as `{...configWithoutStrategy, ...generated}`.
5. Cross-check: the direct output must be JSON-equal to the live render. Views and sections add
   `type`, as `hui-view`/`hui-section` do.

## Gotchas

- **Key order matters.** JSON is written in JS insertion order (WS list order). Don't re-sort.
- `usage_prediction/common_control` is cached per user for 24h on the server, and depends on recorded
  history and the time of day. The non-admin user has no history, so it gets `[]`. Re-capturing on
  another day may change the overview's common-controls section. Always use the recorded response.
- `energy/get_prefs` returns `not_found` on the test instance (no energy config), so the energy
  summary is absent.
- State values of `now()`-based template sensors and `sun.sun` drift over time. Strategies don't read
  them, but `inputs/states.json` and `ws/get_states.json` may churn a little between captures.
- A `pageerror` ("reading 'addEventListener'") appears while some area views render in headless
  Chromium. It comes from a card, not from the strategies, and doesn't affect the output.
