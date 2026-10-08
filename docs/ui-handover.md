# UI Handover (for Astra)

You own the **visual layer** of the native dashboard: how cards, sections and views look and feel. The logic (what
to show, derived from Home Assistant's config and state) lives elsewhere and is being built in parallel. Read
`progress.md` first, then this.

## Ownership

| Area | Owner | Files |
|---|---|---|
| Card composables, view/section layout, theming, icons, animation | **Astra** | `dashboard/src/main/kotlin/.../dashboard/ui/cards/*`, layout parts of `ui/DashboardScreen.kt`, `dashboard/src/main/res/*` |
| Config/state models, strategies, derivations, conditions, actions, data and ViewModel | Logic track | `dashboard-core/**`, `dashboard/.../data/**`, `ui/DashboardViewModel.kt` |

If you need data that a model doesn't expose, add a field to the pure `derive/*Model` function in `:dashboard-core`
along with a unit test, or leave a note in `progress.md` under "Open questions". Don't compute it in a composable.

## How a card gets its content

```
HA config (CardConfig, raw JSON) + HassSnapshot (states, registries, translations)
        │  pure function in :dashboard-core, e.g. tileModel(card, states), headingModel(card), hass.areaCardModel(card)
        ▼
display model (immutable data class: TileModel, HeadingModel, AreaCardModel)
        │  derivedStateOf in the card composable, so it recomposes only when the model changes
        ▼
Compose
```

- `ui/cards/DashboardCard.kt` dispatches on `card.type`. Every type without a native renderer shows `UnsupportedCard`.
- A card receives `State<HassSnapshot?>` and must read it **inside `derivedStateOf`**. That's what keeps one entity
  update from recomposing the whole dashboard.
- Models are deliberately basic. For example, `TileModel.state` is the raw state (`"on"`); translated display
  (`"On"`, `"21.5 °C"`) is coming from the logic track. Design for the final shape: a name, a state line, an icon,
  active/unavailable.
- `tap_action: navigate` already works on any card (`onNavigate`). Other actions (toggle, more-info) are coming. Plan
  for a primary tap plus a long-press (hold) affordance.

## Design brief

- **Native Material 3 Android.** Don't copy the web frontend's look. Match its *information* and *behaviour*: same
  cards, same order, same conditions.
- Use `HATheme` (`:common` `common.compose.theme`): `LocalHAColorScheme` tokens, `HATextStyle`, and `HADimens.SPACE*`.
  No magic numbers, no hardcoded colours.
- Icons: entity and config icons are `mdi:*` names. `:common` depends on an MDI compose library (see how
  `HATopBar` uses `Mdi.ArrowLeft.rememberImageVector()`). A name → vector lookup is needed.
- Sections views (`type: sections`) are a 12-column grid per section, using `grid_options` (`columns`, `rows`) and
  `column_span`. On phones it collapses to one column. The exact sizing rules are being ported; until then, a sensible
  phone layout is fine.
- Priority, by what the default (`/home`) dashboard uses:
  1. tile
  2. heading (with badges)
  3. area (compact, vertical)
  4. home-summary
  5. markdown (header welcome)
  6. empty-state
  7. repairs / updates / discovered-devices
  8. shortcut
  9. picture-entity
- Unavailable/unknown states and the offline "cached" state need clear treatment (offline indicator coming).
- **Strings:** add them to `dashboard/src/main/res/values/strings.xml` (our own module, which avoids upstream merge
  conflicts), never to `:common`.

## Running it

- JDK 21: `export JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`
- Build: `./gradlew :app:assembleMinimalDebug`. Needs `app/google-services.json`; copy it from
  `.github/mock-google-services.json`.
- Test server: `tools/test-ha/up.sh`. It's local HA 2026.7.4 with demo devices, and **the only server to use**.
- Device or emulator:
  1. `adb install -r app/build/outputs/apk/minimal/debug/app-minimal-debug.apk`
  2. `adb reverse tcp:8124 tcp:8124`
  3. Once per install: `python3 tools/test-ha/onboard_app.py`. Never connect to a server found by discovery.
- Open the **"Native dashboard"** launcher icon (debug builds only). It shows the generated home dashboard by
  default; "Test dashboard" exercises many card types.
- Debug builds run StrictMode with FailFast, so any disk or network access on the main thread crashes the app on
  purpose.
- Previews: use `HAThemeForPreview`. Screenshot tests are welcome (`ha-android-testing` skill).
- Run `./gradlew --stop` when idle; the machine is memory constrained.

## Don't

- Don't put logic in composables (filtering, mapping config, computing names or states). Ask for a model field.
- Don't edit upstream modules (`:app`, `:common`, ...) for UI work.
- Don't commit anything from a real Home Assistant instance.
