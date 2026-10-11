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

## Looks and small behaviour

7. Dashboard tabs show the title only (path when untitled); upstream shows the view's icon, and "Unnamed view"
   (`ui/DashboardView.kt:83`; src/panels/lovelace/hui-root.ts:470-490)
8. Toolbar: title always, tabs on a row below; upstream puts the tabs in the toolbar when there are several views,
   with Search, Assist and the menu (`ui/DashboardTopBar.kt:36`; hui-root.ts:586-597)

## Missing features

25. Media control card: no progress bar, browse or join buttons; artwork on top instead of at the side with its
    colours (`ui/cards/MediaControlCard.kt:39`; hui-media-control-card.ts:201,300-344)
26. Area card: icon and name only; upstream is a tile with the area's picture, sensors, alerts and features
    (`ui/cards/AreaCard.kt:32`; hui-area-card.ts:114,643,671-703)
27. Thermostat card: `features` and `show_current_as_primary` not drawn (`ui/cards/ThermostatCard.kt:46`,
    `ui/controls/CircularStateControl.kt:143`; hui-thermostat-card.ts:160,175)
28. Entities card: `header` and `footer` elements not drawn (`ui/cards/EntitiesCard.kt:60`; hui-entities-card.ts:190-202)
37. Drawer: no Notifications entry or badge, no notification dot on the menu button; the profile entry says
    "Profile" instead of the user's name and badge (`ui/NavigationDrawer.kt:125-131`; ha-sidebar.ts:424-563)
38. History: no "Add to dashboard" (`ui/panels/HistoryPanel.kt:215`) — needs dashboard editing, in the README
