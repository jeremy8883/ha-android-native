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
41. Notifications: configurator entities aren't listed (`ui/dialogs/notifications/NotificationDrawer.kt`;
    src/dialogs/notifications/notification-drawer.ts)
42. Energy devices donut: a tap on a slice opens the device; upstream opens it from the slice's outside label, which
    isn't drawn (`ui/lovelace/cards/energy/EnergyDevicesGraphCard.kt`; hui-energy-devices-graph-card.ts)
43. Energy total chip: the tooltip shows on a long press instead of on hover
    (`ui/lovelace/cards/energy/EnergyUsageGraphCard.kt`; hui-energy-graph-chip.ts)
44. Energy "Download data" saves through Android's save-as picker instead of a browser download
    (`ui/lovelace/cards/energy/EnergyCsvDownload.kt`; hui-energy-period-selector.ts)
45. Details YAML view: no syntax colours, fold markers, or copy and full-screen buttons
    (`ui/moreinfo/MoreInfoDetails.kt`; src/components/ha-yaml-editor.ts)
46. Details: the state and attributes cards are the app's grey filled cards; upstream's are outlined
    (`ui/moreinfo/MoreInfoDetails.kt`; src/dialogs/more-info/ha-more-info-details.ts)
47. Assist: thinking text in the primary text colour (upstream: secondary), and its header doesn't reach the bubble's
    edges (`ui/moreinfo/controls/MoreInfoConversation.kt`; src/components/ha-assist-chat.ts)
48. Assist: an answer with no spoken response drops upstream's trailing "…"
    (`ui/moreinfo/controls/MoreInfoConversation.kt`; src/components/ha-assist-chat.ts)
49. Card features (thermostat, area, tile) only draw the types already ported; climate HVAC modes, for one, don't
    show (`ui/lovelace/cardfeatures/TileFeatureControl.kt`; src/panels/lovelace/card-features/)
50. Media control card: a long title is cut short instead of scrolling (`ui/lovelace/cards/MediaControlCard.kt`;
    hui-media-control-card.ts)
51. Area card: `camera_view: live` shows the snapshot (`ui/lovelace/cards/AreaCard.kt`; hui-area-card.ts)

## Decisions (2026-10-12)

- Keep, marked `UPSTREAM-DIFF`: 41 (configurator entities), 42 (donut list under the ring), 44 (save-as picker),
  46 (grey detail cards).
- Fix: 39, 43, 45 (colours and copy only), 47, 48, 49, 50, 51; 53 as a deliberate improvement (digits from the
  visible series).
- 40: not until Settings pages are native (progress.md, Pages outside the dashboards).
- 52: keep as upstream (consistent with the web).
- Clock: 12/24-hour follows the phone's setting (deliberate: the frontend uses the language's format).
