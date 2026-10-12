# HADash agent guide

This guide covers the native dashboard modules: `:dashboard-app` (the HADash app), `:dashboard` (UI and data) and
`:dashboard-core` (pure logic). The root `AGENTS.md` is upstream's and still applies. This file adds the rules for
this fork. `dashboard/AGENTS.md` and `dashboard-core/AGENTS.md` link here.

## Where things are

- **`progress.md`:** the roadmap and handover: what's done, what's next, decisions, notes per session. Read its
  Context section first. Put new roadmap items there.
- **`docs/ui-handover.md`:** UI rules, the card-model contract, and the table from the frontend's CSS to `Css` tokens.
- **`docs/upstream-differences.md`:** differences waiting for the user's decision.
- **`docs/architecture.md`:** how the modules fit together.
- **The frontend:** the version being ported is `~/projects/ha-refs/frontend-20260624.6`, read-only.
- **`tools/test-ha/`:** the local test server. **`tools/golden/`:** captures from the web frontend, for golden tests.
  **`tools/upstream-map.py`:** links composables to frontend files.

## Principles

- **Stay as close to upstream as possible.** Port the frontend's behaviour, layout and sizes. Compare every visible
  change with the web frontend, side by side.
- **The look may be native, the sizes may not differ.** Grey filled cards, native colours and Material widgets are
  fine. Text sizes, padding, gaps and heights match the frontend, so a screen holds the same content as the web.
  Take sizes from the CSS through the `Css` API (`12.cssPx`, `14.cssFontPx`, `Css.Space.S4`, `Css.Text.Body`).
  Never use `HATextStyle` or `HADimens` in `:dashboard`; detekt fails on them.
- **Mark deliberate differences.** Each one gets `// UPSTREAM-DIFF:` where it happens, saying what upstream does
  instead. Ask the user before adding one. List found differences in `docs/upstream-differences.md` for triage.
- **Link every composable.** Every `@Composable` has an `Upstream:` KDoc line. `python3 tools/upstream-map.py check`
  must report 0 problems.
- **Standing decisions:**
  - Date and time fields use Android's pickers.
  - 12/24-hour time follows the phone's setting.
  - Pages that aren't native open in the companion app, after a toast.
  - Never show "empty" when loading failed (see the root guide).

## State, loading and errors

- **Derive from raw state.** Home Assistant's data is the single source: one raw snapshot per server and dashboard
  (states, registries, dashboard config, connection status). Everything shown is derived from it by pure functions
  in `:dashboard-core` and is never stored: the resolved dashboard, visible cards, card models and display text.
  Mutable UI state exists only for transient input, such as a slider being dragged or a field being typed in. Port
  upstream's compute functions rather than inventing new ones. Structure derives from config and registries only, so
  a state change never regenerates the dashboard. See `docs/architecture.md` §4.
- **Cache everything.** Server data is cached on the device indefinitely and survives restarts. A server's cache is
  cleared when the user logs out of it or removes it, not when its sign-in expires. Cached data shows its age.
- **Loading:**
  - With nothing cached, show a full-screen spinner.
  - With cached data, show it at once, with a small loading indicator just below the top bar while it refreshes.
- **Errors:**
  - A failed load with nothing cached shows a full-screen error, or an inline one where that fits better, with a
    retry.
  - A failed refresh keeps showing the cached data and reports the failure quietly, without blocking toasts.
  - A failed action (a toggle, a submit) shows a short error message. Prefer what Home Assistant itself does.
  - Never turn a failure into an empty state (see the root guide): model "not loaded", "failed" and "loaded but
    empty" as distinct types.
- **Indicators overlay, never reflow.** Showing or hiding a loading, error or offline indicator must not push the
  content around. The offline indicator sits at the bottom, stacked with error messages.
- **Offline, controls stay enabled.** Let an action fail and show the failure, rather than disabling controls.

## Building

- **The commit gate.** Run `ktlintFormat` first, then:

  ```
  ./gradlew :dashboard:ktlintCheck :dashboard-core:ktlintCheck :dashboard-app:ktlintCheck \
    :dashboard:detektMain :dashboard-core:detektMain :dashboard-app:detektMain :dashboard-core:test \
    :dashboard:testDebugUnitTest
  python3 tools/upstream-map.py check
  ```

  Detekt's note "3 compiler errors found during analysis" is harmless. Never use `@Suppress`.
- Test against the local test server (`tools/test-ha/`, see its README), never a real installation. The repo is
  public: never commit data, dumps or screenshots from a real server.

## Git

- Work on `native-dashboard`. Commit and push as you go, without asking.
- Never rewrite history or force-push. Commit only your own files: `git commit -- <paths>`, never `-a`.

## Reporting to the user

- Show each visible change as a screenshot of HADash next to the web frontend's, with a plain explanation and little
  jargon.
