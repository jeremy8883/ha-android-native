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


## Missing features

38. History: no "Add to dashboard" (`ui/panels/HistoryPanel.kt:215`) — needs dashboard editing, in the README
