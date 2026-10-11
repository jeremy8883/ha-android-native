# Differences from upstream to triage

Found on 2026-10-11 while linking every composable to its frontend counterpart (`Upstream:` lines,
`tools/upstream-map.py`). None of them has an `// UPSTREAM-DIFF:` marker yet. Not an exhaustive audit: what was
noticed comparing each composable with its frontend file.

For each: **keep** (deliberate: add an `// UPSTREAM-DIFF:` marker) or **fix** (port upstream's behaviour). Paths
on the right are the frontend's (20260624.6).

## Possibly deliberate (decided in earlier sessions, or a native choice)

1. Drawer: pages opening in the companion app are grouped under their own heading; upstream keeps one list in the
   user's order (`ui/NavigationDrawer.kt:176`; src/components/ha-sidebar.ts:392-418)
2. Alarm and lock codes use the system number keyboard; upstream draws its own keypad with clear and submit, a
   "Code" label, a show toggle, and checks the lock's code pattern (`ui/CodeDialog.kt:35`;
   src/dialogs/enter-code/dialog-enter-code.ts:115-200)
3. Sections view on a phone: the content/sidebar switch is two chips; upstream uses a segmented control
   (`ui/DashboardView.kt:227`; src/panels/lovelace/views/hui-sections-view.ts:606-618)
4. Copy light favourites: a list of checkboxes; upstream uses a required multi-entity picker
   (`ui/moreinfo/LightFavoriteDialogs.kt:160`; src/dialogs/more-info/favorites.ts:96-120)
5. Details: the History view is titled "History"; upstream keeps the entity's name (`ui/MoreInfoView.kt:167`;
   src/dialogs/more-info/ha-more-info-dialog.ts:575-580)
6. Login: no "Forgot password?" link (`dashboard-app/.../login/LoginScreen.kt:185`; src/auth/ha-auth-flow.ts:258-264)

## Looks and small behaviour

7. Dashboard tabs show the title only (path when untitled); upstream shows the view's icon, and "Unnamed view"
   (`ui/DashboardView.kt:83`; src/panels/lovelace/hui-root.ts:470-490)
8. Toolbar: title always, tabs on a row below; upstream puts the tabs in the toolbar when there are several views,
   with Search, Assist and the menu (`ui/DashboardTopBar.kt:36`; hui-root.ts:586-597)
9. View header: always card over badges, centred and wrapped; `layout` and `badges_wrap: scroll` ignored
   (`ui/ViewHeader.kt:46-75`; src/panels/lovelace/views/hui-view-header.ts:200-205, 377-439)
10. Badges: no 1px border, `colorSurfaceLow` background, label as big as the state (`ui/ViewHeader.kt:97,113`;
    src/components/ha-badge.ts:67-110)
11. Heading badges: a pill background and primary text; upstream has no background and secondary text
    (`ui/cards/HeadingCard.kt:123,147`; src/components/ha-heading-badge.ts:36)
15. Details: Attributes hidden when there are none, and no card around state and attributes
    (`ui/MoreInfoSheet.kt:271`; src/dialogs/more-info/ha-more-info-details.ts:70-103)
21. History timeline tooltip: no entity name, no duration, start and end on one line without seconds
    (`ui/charts/TimelineChartView.kt:113`; src/components/chart/state-history-chart-timeline.ts:135-162)
22. Energy graph legends: hidden items crossed out (upstream: empty circle); no collapsing to 6 on phones with
    "show more" (`ui/cards/energy/EnergyUsageGraphCard.kt:134`; src/components/chart/ha-chart-base.ts:410-500)
23. Energy total chip: filled, no tooltip; upstream is outlined with a tooltip
    (`ui/cards/energy/EnergyUsageGraphCard.kt:98`; cards/energy/common/hui-energy-graph-chip.ts:21-27)
24. History panel: pickers stay enabled while loading (`ui/panels/HistoryPanel.kt:215`; ha-panel-history.ts:163,174)

## Missing features

25. Media control card: no progress bar, browse or join buttons; artwork on top instead of at the side with its
    colours (`ui/cards/MediaControlCard.kt:39`; hui-media-control-card.ts:201,300-344)
26. Area card: icon and name only; upstream is a tile with the area's picture, sensors, alerts and features
    (`ui/cards/AreaCard.kt:32`; hui-area-card.ts:114,643,671-703)
27. Thermostat card: `features` and `show_current_as_primary` not drawn (`ui/cards/ThermostatCard.kt:46`,
    `ui/controls/CircularStateControl.kt:143`; hui-thermostat-card.ts:160,175)
28. Entities card: `header` and `footer` elements not drawn (`ui/cards/EntitiesCard.kt:60`; hui-entities-card.ts:190-202)
30. Chart legends: no long-press "show only this one" (`ui/charts/HistoryLegend.kt:87`,
    `ui/cards/energy/EnergyUsageGraphCard.kt:134`; ha-chart-base.ts:1212-1261)
31. Energy devices donut: slices and legend don't open the device's details; the legend can't hide devices
    (`ui/cards/energy/EnergyDevicesGraphCard.kt:202`; hui-energy-devices-graph-card.ts:273-284,595-617)
32. Energy date selection: no "Download data"; `disable_compare` ignored
    (`ui/cards/energy/EnergyDateSelectionCard.kt:145,89`; hui-energy-period-selector.ts:254,261-267)
35. Assist chat: no "thinking" section or tool calls (`ui/moreinfo/MoreInfoConversation.kt:135`;
    ha-assist-chat.ts:187-260) — in the README already
36. Details: no YAML view of the attributes (`ui/MoreInfoView.kt:103`; ha-more-info-dialog.ts:819-829)
37. Drawer: no Notifications entry or badge, no notification dot on the menu button; the profile entry says
    "Profile" instead of the user's name and badge (`ui/NavigationDrawer.kt:125-131`; ha-sidebar.ts:424-563)
38. History: no "Add to dashboard" (`ui/panels/HistoryPanel.kt:215`) — needs dashboard editing, in the README
