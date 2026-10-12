# UI Handover (for Astra)

You own the **visual layer** of the native dashboard: how cards, sections and views look and feel. The logic (what
to show, derived from Home Assistant's config and state) lives elsewhere and is being built in parallel. Read
`progress.md` first, then this.

## Ownership

| Area | Owner | Files |
|---|---|---|
| Card composables, view/section layout, theming, icons, animation | **Astra** | `dashboard/src/main/kotlin/.../dashboard/ui/lovelace/*`, layout parts of `ui/DashboardScreen.kt`, `dashboard/src/main/res/*` |
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

- `ui/lovelace/common/DashboardCard.kt` dispatches on `card.type`. Every type without a native renderer shows `UnsupportedCard`.
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
- Colours come from `HATheme` (`:common` `common.compose.theme`): `LocalHAColorScheme` tokens, no hardcoded colours.
  Sizes come from the frontend's CSS through `ui/theme/Css.kt` (see "Sizes from the frontend's CSS" below), never
  from the companion app's presets (`HATextStyle`, `HAFontSize`, `HADimens`, `HARadius`, `HABorderWidth`):
  `dashboard/detekt.yml` fails `:dashboard:detektMain` on importing them.
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
- Don't differ from upstream's look or behaviour silently: mark each deliberate difference with a
  `// UPSTREAM-DIFF:` comment saying what upstream does instead (see `progress.md`, Context).
- Don't convert sizes when porting: copy the frontend's CSS value as is. A raw px value is `n.cssPx` (dp) or
  `n.cssFontPx` (sp, for `font-size`, `line-height` and `letter-spacing`); a CSS variable is the `Css.*` token named
  after it (table below). The `Upstream:` file's CSS is where to read them.
- Don't add a composable without its `Upstream:` line: every `@Composable` function's KDoc names the frontend
  component it ports and its file (``Upstream: `hui-tile-card` (src/panels/lovelace/cards/hui-tile-card.ts)``), or
  `Upstream: part of …` for a piece of one, or `Upstream: none (native: why)`. The link only goes from here to the
  frontend, which is never changed. `tools/upstream-map.py check` lists what is missing or points nowhere; `map`
  lists each frontend file's composables; `changed`, given the frontend files changed between two versions, lists
  the composables to review.
- Put a file in the `dashboard/ui` package that mirrors the frontend folder of what it ports: `lovelace/` (hui-root),
  `lovelace/{cards,cards/energy,entityrows,cardfeatures,headerfooter,views,components,common}` (the same folders of
  `src/panels/lovelace`), `moreinfo/`, `moreinfo/{controls,components}` (`src/dialogs/more-info`), `dialogs/<name>`
  (`src/dialogs`), `panels/<panel>` (`src/panels`), `components/` and its `chart`, `targetpicker`, `mediaplayer`,
  `selector` (`src/components`), `statecontrol/` (`src/state-control`), `statesummary/` (`src/state-summary`). The
  app's own shell (screen, view model, navigation) stays at the top of `ui`.
- Don't port the frontend's date or time pickers for fields: use Android's own (Material 3 `DatePicker`,
  `DateRangePicker`, `TimePicker`). Only calendars drawn in a page (the Calendar panel, the calendar card) are
  ported (see `progress.md`, Context).

## Sizes from the frontend's CSS

1 CSS px is 1 dp, and a font px is 1 sp (equal to dp at the default font scale, as CSS px are at the browser's default
zoom). So a value is ported with the same number, never converted:

| CSS | Kotlin |
|---|---|
| raw length, `padding: 12px` | `12.cssPx` (`Dp`) |
| raw `font-size: 36px`, `line-height: 16px`, `letter-spacing: 0.4px` | `36.cssFontPx`, `16.cssFontPx`, `0.4.cssFontPx` (`TextUnit`) |
| unitless `line-height: 1.4`, `font-size: 1.2em` of 14px | `1.4.em`, `Css.FontSize.M * 1.2f` |
| `--ha-font-size-xs` … `--ha-font-size-5xl` (10, 12, 14, 16, 20, 24, 28, 32, 40) | `Css.FontSize.Xs`, `S`, `M`, `L`, `Xl`, `X2l`, `X3l`, `X4l`, `X5l` |
| `--ha-font-size-scale` | applied inside `Css.FontSize` only (`FONT_SIZE_SCALE` in `Css.kt`, 1), as upstream; raw px sizes ignore it |
| `--ha-font-weight-light` / `normal` / `medium` / `bold` | `Css.FontWeight.Light` / `Normal` / `Medium` / `Bold` |
| `--ha-font-weight-body` / `heading` / `action` | `Css.FontWeight.Body` / `Heading` / `Action` |
| `--ha-line-height-condensed` / `normal` / `expanded` (1.2, 1.6, 2) | `Css.LineHeight.Condensed` / `Normal` / `Expanded` (em) |
| `--ha-space-1` … `--ha-space-20` (n × 4px) | `Css.Space.S1` … `Css.Space.S20` |
| `--ha-border-radius-square` / `sm` / `md` / `lg` / `xl` / `2xl` … `6xl` / `pill` | `Css.Radius.Square` / `Sm` / `Md` / `Lg` / `Xl` / `X2l` … `X6l` / `Pill` |
| `--ha-border-radius-circle` (50%) | `CircleShape` |
| `--ha-border-width-sm` / `md` / `lg` | `Css.BorderWidth.Sm` / `Md` / `Lg` |

Text styles (`Css.Text`, composable getters):

| Frontend | Kotlin |
|---|---|
| body text (`haStyle` `:host`: `--ha-font-size-m`, `--ha-line-height-normal`) | `Css.Text.Body`: centred, secondary colour; its line height is fixed at 22.4, so a copy at another `fontSize` sets `lineHeight` too (`Css.LineHeight.Normal` when the element inherits the body's) |
| body text in `--primary-text-color`, start-aligned | `Css.Text.Primary` |
| `ha-card` `.card-header` | `Css.Text.CardHeader` (padding: `CardHeaderPadding`, `CardHeaderAboveContentPadding`) |
| `ha-dialog-header` `.header-title` (every `ha-dialog`'s title) | `Css.Text.DialogTitle` |
| `haStyle` `h1` / `h2` / `h3` | `Css.Text.H1` / `H2` / `H3` |

Values that aren't the frontend's (a Material component's own size, a native-only layout) stay plain `dp`/`sp`.

