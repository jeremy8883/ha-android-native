# HADash

An Android app that shows your Home Assistant dashboards natively (drawn by the app, not a web page), alongside the
official Home Assistant companion app. It logs in to your server itself and draws the dashboards with the same rules
as Home Assistant's own frontend (version 20260624.6).

## When something isn't supported yet

Anything the app can't do yet opens in the official Home Assistant companion app (or the browser, when the companion
app isn't installed). A message says so first: "This isn't available here yet. Opening it in the Home Assistant app."

Cards the app can't draw natively are different: they're shown in place, inside the dashboard, by Home Assistant's
own frontend in a small web view, so the dashboard stays complete without leaving the app.

## What isn't supported natively yet

Last updated 2026-10-11.

### Pages (open in the companion app)

- **Map**: the Map page in the drawer, and any other dashboard built by a generator the app doesn't have yet
  (custom "strategy" dashboards). The page offers an "Open in the Home Assistant app" button.
- **Calendar**: the Calendar page.
- **Media**: the media browser.
- **Settings**: everything under Settings (integrations, devices, entities, automations, scenes, scripts, areas,
  zones, repairs, updates, people, add-ons, system), and links into it. That includes:
  - an automation's trace ("View trace" in Activity);
  - the device, scenes and automations headings on the Overview;
  - the Repairs, Updates and Discovered devices cards. Upstream's Discovered devices card opens the add-integration
    dialog; here it opens the integrations page.
- **Profile**: your user profile and its settings.
- **Notifications**: Home Assistant's notifications list.
- **Developer tools**, and **custom pages** added by integrations or add-ons (such as Music Assistant).

### Natively supported pages

Overview, your own dashboards, the Lights, Climate, Security and Maintenance summaries, Energy, Activity (logbook),
History and To-do lists.

### Dashboard cards (shown by the web view inside the app)

Calendar, Clock, Distribution, Entity filter, History graph, Humidifier, Iframe (web page), Logbook, Map, Picture,
Picture elements, Picture glance, Plant status, Shopping list, Statistic, Statistics graph, Toggle group, and any
custom card.

### Entity details

- **"More details"**: the full details window opens in the companion app. That's where an entity's settings, its
  device, related items, and "add to" a dashboard or automation live.
- **Create zone** (on a person's map) opens the zones settings in the companion app; upstream opens the zone editor
  with the person's location filled in.
- **Cameras that only offer WebRTC** show their slower MJPEG stream instead.
- **Script fields** of the rarer kinds (areas, devices, colours, locations and others) are edited as text.
- **Light colours**: the colour picker has no eyedropper.

### Dashboards

- **Editing dashboards** isn't possible in the app yet: adding, moving and removing cards, card settings, and the
  code editor. This is on the roadmap as a fully native editor.
- **Toolbar buttons**: the dashboard toolbar has no Search, Assist or Edit buttons yet.

### To-do lists

- Adding items by voice (Assist).
- Items are reordered with up and down buttons; upstream drags them.

### History and Activity

- History charts zoom with a pinch or double tap. After zooming, every chart's time labels change to fit; upstream
  keeps the other charts' first label style.
- The History page's "Add current view as card" menu item is missing (it needs dashboard editing).

### Companion app features this app doesn't have

Phone sensors (location, battery…), push notifications and their actions, widgets, quick settings tiles, device
controls, Wear OS, Android Auto, NFC tags, and voice Assist from the phone.

### Sign-in and servers

- Client certificates (mTLS) (planned).
- Home and away addresses: a local address used on your home Wi-Fi and an outside one elsewhere, and editing a
  server's name and addresses after adding it (planned). For now a server has one address.

Servers behind a sign-in page of their own (Cloudflare Access, Authelia and similar) are supported: the app opens
the server's sign-in page when it finds one, and "Sign in with the browser" opens it on any server.
