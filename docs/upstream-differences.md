# Differences from upstream to triage

Found on 2026-10-11 while linking every composable to its frontend counterpart (`Upstream:` lines,
`tools/upstream-map.py`). None of them has an `// UPSTREAM-DIFF:` marker yet. Not an exhaustive audit: what was
noticed comparing each composable with its frontend file.

For each: **keep** (deliberate: add an `// UPSTREAM-DIFF:` marker) or **fix** (port upstream's behaviour). Paths
on the right are the frontend's (20260624.6).

## Missing features

38. History: no "Add to dashboard" (`ui/panels/history/HistoryPanel.kt:215`) — needs dashboard editing, in the README

## Left by the fixes (found 2026-10-11, keep or fix)

40. Drawer: the Settings entry has no updates and repairs count (`ui/components/NavigationDrawer.kt`;
    src/components/ha-sidebar.ts)
49. Card features (thermostat, area, tile) only draw the types already ported; climate HVAC modes, for one, don't
    show (`ui/lovelace/cardfeatures/TileFeatureControl.kt`; src/panels/lovelace/card-features/)

## Decisions (2026-10-12)

- Keep, marked `UPSTREAM-DIFF`: 41 (configurator entities), 42 (donut list under the ring), 44 (save-as picker),
  46 (grey detail cards).
- Fix: 39, 43, 45 (colours and copy only), 47, 48, 49, 50, 51; 53 as a deliberate improvement (digits from the
  visible series).
- 40: not until Settings pages are native (progress.md, Pages outside the dashboards).
- 52: keep as upstream (consistent with the web).
- Clock: 12/24-hour follows the phone's setting (deliberate: the frontend uses the language's format). Done, marked
  `UPSTREAM-DIFF` where the dashboard's formats are made (`ui/DashboardViewModel.kt`, `structureInputs`).
- Done: 42 and 44 marked `UPSTREAM-DIFF`; 43 (the chip's tooltip shows on a tap, as the web's on a phone); 53 (the
  y axis's digits follow the shown series in the energy bar graphs, the power sources graph and history line
  charts, marked `UPSTREAM-DIFF` at `shownYAxisFractionDigits`).
- Done (fix-k): 41 and 46 marked `UPSTREAM-DIFF`; 45 (CodeMirror's YAML colours and the copy button, fold markers
  and the other toolbar buttons marked `UPSTREAM-DIFF`); 47; 48; 50 (the title scrolls once tapped); 51 (the area
  card streams the camera). Attribute values are translated as `ha-attribute-value` does ("Off, Heat, Cool").
