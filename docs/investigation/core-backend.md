# HA Core backend: dashboards and entity state (server side)

Source: `home-assistant/core` shallow clone at `/home/jeremy/projects/ha-refs/core`,
commit `767937befd80fae899a4718b65c7b7626024466a` (`homeassistant/const.py`: `2026.11.0.dev0`).
All paths are relative to `homeassistant/`. Note: this tree validates with `probatio` (the
voluptuous replacement); semantics are the same (`Required`/`Optional`/`Any`).

---

## 0. Envelope, ids, errors (applies to everything below)

- Endpoint: `/api/websocket` (`components/websocket_api/const.py` `URL`).
- Every client message: `{"id": <int>, "type": "<cmd>", ...}`. `ActiveConnection.async_handle`
  (`connection.py`) rejects: non-dict, missing/0/negative/non-int `id`, or non-str `type` ->
  `invalid_format` "Message incorrectly formatted."; `id <= last_id` -> `id_reuse`
  ("Identifier values have to increase"); unknown type -> `unknown_command`. Ids must be
  strictly increasing per connection, start at 1.
- The client may also send a JSON **array** of commands in one frame (`http.py`
  `_async_websocket_command_phase` iterates lists).
- Result (`messages.result_message`): `{"id", "type":"result", "success":true, "result":...}`.
- Error (`messages.error_message`): `{"id", "type":"result", "success":false, "error":{"code","message"}}`
  plus `translation_key`, `translation_placeholders`, `translation_domain` **only if** a
  translation key exists.
- Event (`messages.event_message`): `{"id": <subscription msg id>, "type":"event", "event": ...}`.
- Error codes (`websocket_api/const.py`): `id_reuse`, `invalid_format`, `not_allowed`,
  `not_found`, `not_supported`, `home_assistant_error`, `service_validation_error`,
  `unknown_command`, `unknown_error`, `unauthorized`, `timeout`, `template_error`.
  Mapping of handler exceptions in `ActiveConnection.async_handle_exception`:
  `Unauthorized`->`unauthorized` ("Unauthorized"), `Invalid`->`invalid_format`,
  `TimeoutError`->`timeout`, `HomeAssistantError`->`home_assistant_error`, else `unknown_error`.
  Component-specific codes exist too: lovelace uses `config_not_found` and `error`;
  frontend uses `unknown_version`.
- `require_admin` (`decorators.py`) raises `Unauthorized` -> `unauthorized` error.
- Unsubscribe any subscription: `{"type":"unsubscribe_events","subscription":<sub msg id>}`
  (`commands.handle_unsubscribe_events`; unknown -> `not_found` "Subscription not found.").
  This works for every subscription kind (they are all stored in `connection.subscriptions[msg_id]`).
  All subscriptions are torn down on disconnect (`async_handle_close`).

## 1. Auth flow (`components/websocket_api/auth.py`, `http.py`)

1. Server sends right after upgrade: `{"type":"auth_required","ha_version":"2026.11.0.dev0"}`.
2. Client must send within `AUTH_MESSAGE_TIMEOUT = 10` s (`http.py`):
   `{"type":"auth","access_token":"<token>"}` (`api_password` is the legacy exclusive alternative). No `id`.
3. Success: `{"type":"auth_ok","ha_version":"..."}`. Failure: `{"type":"auth_invalid","message":"..."}`
   then disconnect (`"Invalid access token or password"`, or schema error text, or
   `async_user_not_allowed_do_auth` reason). Failed logins feed the IP ban counter
   (`process_wrong_login`) -> don't retry-loop with a bad token.
4. Token revocation closes the socket (`async_register_revoke_token_callback(..., cancel_ws)`).
- Access token: OAuth `/auth/token` (`components/auth/__init__.py`, `grant_type`
  `authorization_code` / `refresh_token`). Long-lived tokens via `auth/long_lived_access_token`.
- Server-side heartbeat: `web.WebSocketResponse(heartbeat=55)` -> WS ping frames every 55 s;
  client must answer at the frame level (OkHttp does). App-level ping is separate (below).

## 2. Connection-level commands (`components/websocket_api/commands.py`)

| type | schema | admin | result |
|---|---|---|---|
| `ping` | — | no | **not** a result: `{"id":N,"type":"pong"}` (`pong_message`) |
| `supported_features` | `features: {str:int}` | no | `null`. `{"coalesce_messages":1}` enables batching (`connection.set_supported_features`, `FEATURE_COALESCE_MESSAGES`) |
| `get_config` | — | no | `hass.config.as_dict()` (`core_config.py`): `version`, `state` ("RUNNING" etc.), `components`[], `unit_system`{length,accumulated_precipitation,area,mass,pressure,temperature,volume,wind_speed}, `time_zone`, `language`, `country`, `currency`, `location_name`, `latitude`, `longitude`, `elevation`, `radius`, `internal_url`, `external_url` (dropped for `local_only` users), `config_dir`, `config_source`, `safe_mode`, `recovery_mode`, `debug`, `allowlist_external_dirs`/`_urls`, `whitelist_external_dirs`, `logging` |
| `get_states` | — | no | array of full states (`State._as_dict`, `core.py`): `entity_id, state, attributes, last_changed, last_reported, last_updated` (ISO strings), `context{id,parent_id,user_id}`. Filtered by user read permission (`_async_get_allowed_states`) |
| `get_services` | — | no | service descriptions JSON |
| `subscribe_events` | `event_type` (default `*` MATCH_ALL) | non-admins only for allowlist | result `null`, then events `{event_type,data,origin,time_fired,context}` (`Event._as_dict`) |
| `call_service` | see §2.2 | no | `{"context":{...}, "response"?:...}` |
| `render_template` | see §2.3 | **no** (not decorated) | subscription |
| `subscribe_entities` | see §2.1 | no | subscription |
| `entity/source`, `extract_from_target`, `get_*_for_target`, `manifest/list`, `manifest/get`, `slugify`, `validate_config`, `subscribe_condition` | | no | |
| `subscribe_trigger`, `test_condition`, `execute_script`, `fire_event`, `subscribe_system_state`, `integration/descriptions` | | **yes** | |

Non-admin `subscribe_events` allowlist (`auth/permissions/events.py` `SUBSCRIBE_ALLOWLIST`):
`area_registry_updated, component_loaded, core_config_updated, device_registry_updated,
entity_registry_updated, repairs_issue_registry_updated, lovelace_updated, panels_updated,
recorder_5min_statistics_generated, recorder_hourly_statistics_generated, service_registered,
service_removed, shopping_list_updated, state_changed, themes_updated, label_registry_updated,
labs_updated, category_registry_updated, floor_registry_updated`. Anything else -> `unauthorized`.
`state_changed` is per-event permission filtered (`_forward_events_check_permissions`).
Omitting `event_type` (MATCH_ALL) as non-admin is refused.

### 2.1 `subscribe_entities` (compressed state stream)

Schema (`handle_subscribe_entities`): `entity_ids` (optional, `cv.entity_ids`: list or
comma string) **plus** `INCLUDE_EXCLUDE_BASE_FILTER_SCHEMA` (`helpers/entityfilter.py`):
`include: {domains:[], entity_globs:[], entities:[]}`, `exclude: {...same}`. Both filters
AND together; empty -> everything the user may read. Permissions re-checked per event.

Sequence: result `{"success":true,"result":null}` FIRST, then one event with all current
states, then diffs. The listener is attached before the snapshot without awaiting
(no race; comment in source). Snapshot (`_send_handle_entities_init_response`):
`{"id":N,"type":"event","event":{"a":{"light.x":<compressed>, ...}}}` (may be `{"a":{}}`).

Compressed state (`State.as_compressed_state`, `core.py`; keys in `const.py`
`COMPRESSED_STATE_*`; TypedDict `CompressedState`):
- `s`: state string
- `a`: attributes object
- `c`: context — **string** (context id) if `parent_id` and `user_id` are both null,
  otherwise object `{"id","parent_id","user_id"}`
- `lc`: last_changed, float epoch seconds
- `lu`: last_updated, float epoch seconds — **omitted when equal to `lc`** (client: `lu = lu ?: lc`)
- no `last_reported`, no `entity_id` (it's the map key)

Event shapes (`messages._state_diff_event`; constants `ENTITY_EVENT_ADD="a"`,
`ENTITY_EVENT_CHANGE="c"`, `ENTITY_EVENT_REMOVE="r"`, `STATE_DIFF_ADDITIONS="+"`,
`STATE_DIFF_REMOVALS="-"`). One event per state_changed (one entity per message):
- removed (`new_state is None`): `{"r":["light.x"]}`
- added (`old_state is None`): `{"a":{"light.x":<compressed>}}`
- changed: `{"c":{"light.x":{"+":{...}, "-"?:{"a":["attr_removed",...]}}}}`. `+` may contain:
  - `s` if state changed
  - `lc` if last_changed changed (**then `lu` is NOT sent** — set lu = lc), **elif** `lu` if last_updated changed
  - `c`: if parent_id changed -> `{"parent_id":..}`; if user_id changed -> adds/creates `{"user_id":..}`;
    if id changed -> adds `"id"` into that dict, **or** is the bare id **string** if neither
    parent_id nor user_id changed. Client: string => replace only the context id;
    object => merge its keys into the stored context.
  - `a`: only added/changed attribute keys (merge into existing attrs)
  - `-` only for removed attributes: `{"a":[keys]}`
  - `+` is always present but may be `{}` if nothing tracked differs — treat as no-op.
    `last_reported` changes are not sent at all (different event, `state_reported`).
- Serialization is cached per event across subscribers (`cached_state_diff_message`, `lru_cache(128)`).

### 2.2 `call_service` (`handle_call_service`)

Schema: `domain` str, `service` str, `target` (`cv.ENTITY_SERVICE_FIELDS`: `entity_id`,
`device_id`, `area_id`, `floor_id`, `label_id`; each str/list or `"none"`), `service_data`
dict, `return_response` bool (default false). Always `blocking=True`, so the result arrives
after the service finishes. Result: `{"context":{id,parent_id,user_id}}` plus `"response"`
when `return_response` true. Errors: `not_found` ("Service d.s not found."), child service
missing -> `home_assistant_error` with `translation_key="child_service_not_found"`,
`invalid_format` (schema), `service_validation_error` ("Validation error: ..."; also used when
`return_response` is true for a service that doesn't support it —
`service_does_not_support_response`, or false for one that requires it —
`service_lacks_response_request`, `core.py` ~2911), `home_assistant_error`, `unknown_error`.
Use `context.id` to correlate the resulting state change (`c` in compressed state).

### 2.3 `render_template` (`handle_render_template`)

Schema: `template` str, `entity_ids?`, `variables?` dict, `timeout?` float, `strict` (false),
`report_errors` (false). If `timeout` given and render would exceed it -> `template_error`
"Exceeded maximum execution time of Xs". Template parse error -> `template_error`. Else
result `null`, then events `{"result": <rendered>, "listeners": {"all":bool,"entities":[],"domains":[],"time":bool}}`
on every re-render (tracked via `async_track_template_result`; rate limiting is inside the
template tracker). Errors as events `{"error": "...", "level": "ERROR"|"WARNING"}` only if
`report_errors`. `result` is native-typed (may be number/bool/list, not just string).

### 2.4 Message volume / size / coalescing (`http.py`, `const.py`)

- Outgoing queue limits: `MAX_PENDING_MSG = 4096` -> server disconnects ("Client unable to keep
  up"); staying above `PENDING_MSG_PEAK = 1024` for `PENDING_MSG_PEAK_TIME = 10` s also disconnects.
  `PENDING_MSG_MAX_FORCE_READY = 256` caps a coalesced batch. Client must read promptly.
- With `coalesce_messages`, the writer sends `[msg1,msg2,...]` JSON arrays when >1 message is ready
  (`_writer`); single messages still go as objects. Client parser must accept both.
- No explicit `max_msg_size` set on the server's `WebSocketResponse` -> aiohttp default
  (4 MiB) for **incoming** client frames. Writer drain limit raised to 1 MiB after auth
  (`_async_increase_writer_limit`, comment: entity registry is the largest typical message).
  Outgoing messages have no cap — expect multi-MB `get_states` / `subscribe_entities` snapshot
  / `config/entity_registry/list` on big installs.
- Binary frames: first byte = handler id (`async_handle_binary`); only used by assist/voice.

## 3. Lovelace (`components/lovelace/`)

### 3.1 Loading model (`__init__.py async_setup`, `dashboard.py`)

`hass.data[LOVELACE_DATA].dashboards: dict[url_path|None, LovelaceConfig]`:
- `None` -> `LovelaceStorage(hass, None)` default, store key `.storage/lovelace`, `config = None`.
- YAML dashboards from `configuration.yaml` `lovelace: dashboards:` -> `LovelaceYAML`
  (file `filename`), registered as panels with `config={"mode":"yaml"}`.
- Storage dashboards from `.storage/lovelace_dashboards` (`DashboardsCollection`) ->
  `LovelaceStorage(hass, item)`, store key `lovelace.{item["id"]}`, panel `config={"mode":"storage"}`.
- Legacy `lovelace: mode: yaml` (deprecated, "Remove in 2026.8", repair `yaml_mode_deprecated`)
  is converted to a YAML dashboard with url_path `lovelace` using `ui-lovelace.yaml`.
- Migration `_async_migrate_default_config` (storage mode): if `.storage/lovelace` has a config,
  it is moved into a **storage dashboard with url_path `lovelace`** (title from onboarding
  translation "Overview", icon `mdi:view-dashboard`) and system data `core.default_panel`
  is set to `"lovelace"`. So on current HA, a saved default dashboard shows up in the
  dashboards list as `url_path:"lovelace"`.
- `_async_ensure_default_panel` always registers a built-in `lovelace` panel (no title/config)
  if none exists, for backwards compatibility.
- Onboarding creates a `map` dashboard whose config is `{"strategy":{"type":"map"}}`
  (`_create_map_dashboard`). **Configs can be strategies** (`{"strategy":{...}}`), not views —
  the renderer must handle/skip strategy-generated dashboards.

Resolution for `lovelace/config*` (`websocket._handle_errors`): `url_path` omitted/null ->
`dashboards["lovelace"]` if it exists, else `dashboards[None]`. Unknown url_path ->
error `config_not_found` "Unknown config specified: <url_path>".

### 3.2 Commands

| type | params | admin | result / errors |
|---|---|---|---|
| `lovelace/config` | `url_path?` (str/null), `force` (bool, default false) | no | raw dashboard config object (`{"views":[...], "title"?...}` or `{"strategy":{...}}`), sent as cached json fragment. `ConfigNotFound` -> `{"code":"config_not_found","message":"No config found."}`; other `HomeAssistantError` -> code `"error"` |
| `lovelace/config/save` | `config` (dict or str), `url_path?` | **yes** | `null`; YAML dashboards -> `error` "Not supported"; recovery mode -> `error` |
| `lovelace/config/delete` | `url_path?` | **yes** | `null` (storage: removes file, config back to None -> auto-gen) |
| `lovelace/info` | — | no | `{"resource_mode":"yaml"|"storage"}` |
| `lovelace/resources` (+ `/list`) | — | no | list of `{id, type, url}` (storage) / `{type, url}` (YAML); `type` in `js|css|module|html`; `[]` in safe mode. Lazy-loads on first call. The bare `lovelace/resources` alias says "remove in 2025.1" but is still registered |
| `lovelace/resources/create` | `res_type`, `url` | yes (storage resource mode only) | item (stored as `type`) |
| `lovelace/resources/update` | `resource_id`, `res_type?`, `url?` | yes | item |
| `lovelace/resources/delete` | `resource_id` | yes | `null` |
| `lovelace/resources/subscribe` | — | no | collection subscription (§3.4) |
| `lovelace/dashboards/list` | — | **no** | see §3.3 — includes YAML + storage dashboards, **not** filtered by `require_admin` |
| `lovelace/dashboards/create` | `url_path` (must contain `-` unless `allow_single_word`; must not collide with an existing panel), `title`, `icon?`, `require_admin` (false), `show_in_sidebar` (true), `mode` ("storage" only), `allow_single_word?` | yes | created item |
| `lovelace/dashboards/update` | `dashboard_id`, `title?`, `icon?` (null removes), `require_admin?`, `show_in_sidebar?` | yes | updated item; unknown -> `not_found` |
| `lovelace/dashboards/delete` | `dashboard_id` | yes | `null` |
| `lovelace/dashboards/subscribe` | — | no | collection subscription (storage dashboards only) |

`force` semantics: `LovelaceStorage.async_json` ignores it (in-memory). `LovelaceYAML._load_config`:
without force returns cache if file mtime older than cache time; with force re-reads YAML
from disk; if the file was re-read and a cache already existed, fires `lovelace_updated`.
`ConfigNotFound` raised by: storage config never saved / deleted (`data["config"] is None`),
recovery mode (storage), YAML file missing (`FileNotFoundError`). Frontend reacts to
`config_not_found` by auto-generating ("auto-gen" mode in `async_get_info`; `MODE_AUTO`).

### 3.3 Dashboard metadata shape (`lovelace/dashboards/list` = `[d.config for d in dashboards.values() if d.config]`)

- Storage item (`DictStorageCollection._create_item` = `{"id": id} | data`):
  `{"id":"my_dash","url_path":"my-dash","mode":"storage","title":"..","icon"?:"mdi:..","show_in_sidebar":true,"require_admin":false}`.
  `id = slugify(url_path)` with `_2` suffix on collision (`IDManager.generate_id`), so
  **id != url_path** (hyphens become underscores). `icon` key absent when unset.
- YAML entry: `{"mode":"yaml","filename":"..","title","icon"?,"show_in_sidebar","require_admin","url_path"}` — no `id`.
- The implicit default (url_path None) is not listed.
- Title/icon/sidebar/admin can additionally be overridden per panel via
  `frontend/update_panel` (admin; stored in panels config) — those overrides are only visible
  in `get_panels`, not in `lovelace/dashboards/list`. For a sidebar, prefer `get_panels`
  (`component_name=="lovelace"`, `config.mode`), which also filters `require_admin` for
  non-admins (§4.1).

### 3.4 Events

- `lovelace_updated` (`const.EVENT_LOVELACE_UPDATED`), fired by `LovelaceConfig._config_updated`:
  data `{"url_path": <str|null>}` (null = default/None dashboard). Fired on save, delete, and
  YAML reload-with-change. Non-admins may subscribe (allowlisted).
  Note: a dashboard migrated to url_path `lovelace` fires `"lovelace"`, not null.
- Dashboard metadata create/update/delete fire **`panels_updated`** (no data) via
  `frontend.async_register_built_in_panel` / `async_remove_panel` — not `lovelace_updated`.
  Storage dashboard deletion also calls `LovelaceStorage.async_delete` -> `lovelace_updated`.
- Collection subscribe (`helpers/collection.py StorageCollectionWebsocket._ws_subscribe`):
  result `null`, then event = list of `{"change_type":"added"|"updated"|"removed","<model>_id":id,"item":{...}}`
  (initial event lists all items as `added`). `<model>_id` = `dashboard_id` / `resource_id`.

## 4. Frontend component commands (`components/frontend/__init__.py`, `storage.py`)

### 4.1 `get_panels` (no admin)
Result: `{url_path: PanelResponse}` with `component_name, icon, title, default_visible,
config, url_path, require_admin, config_panel_domain, show_in_sidebar` (`Panel.to_response`,
overrides from `frontend/update_panel` applied). Panels with effective `require_admin` are
omitted for non-admins. Lovelace panels: `component_name:"lovelace"`, `config:{"mode":"storage"|"yaml"}`
(default `lovelace` panel may have `config:null`, `title:null`). Change event: `panels_updated`.

### 4.2 Themes
- `frontend/get_themes` -> `{"themes":{name:{css-var: value, ..., "modes"?:{"light"?:{..},"dark"?:{..}}}}, "default_theme":"default"|name, "default_dark_theme": name|null}`.
  In safe/recovery mode: `{"themes":{}, "default_theme":"default"}` (no dark key).
- No dedicated subscribe command: subscribe to event `themes_updated` (fired by
  `update_theme_and_fire_event` after `frontend.set_theme` / `frontend.reload_themes`), then re-fetch.
  Theme var keys are CSS variable names without `--` (e.g. `primary-color`).

### 4.3 Translations / icons
- `frontend/get_translations`: `language` (req), `category` (req; e.g. `entity`, `state`,
  `entity_component`, `services`, `title`), `integration?` (str|list), `config_flow?` ->
  `{"resources": {"component.<domain>.<category>...": "string", ...}}` flat dotted keys
  (`helpers/translation.async_get_translations`).
- `frontend/get_icons`: `category` in `{"conditions","entity","entity_component","services","triggers"}`,
  `integration?` -> `{"resources": {domain: {...icons.json section...}}}` (`helpers/icon.async_get_icons`).
- `frontend/get_version` -> `{"version":"<home-assistant-frontend pin>"}` or error `unknown_version`.

### 4.4 User / system data (`storage.py`)
- `frontend/get_user_data` `key?` -> `{"value": data[key] | all}`; `frontend/set_user_data` `key`,`value`.
- `frontend/subscribe_user_data` `key?`: **event is sent BEFORE the result** (`on_data_update()`
  then `send_result`) — client must route events by id before the result arrives. Event
  `{"value": ...}` on each change.
- `frontend/get_system_data` / `subscribe_system_data` (`key` required; set is admin).
  Key `"core"` holds `{"default_panel": "lovelace"|...}` (written by lovelace migration).
- `frontend/subscribe_extra_js` (custom card JS URLs; irrelevant natively except to detect custom cards).

## 5. Registries (`components/config/*_registry.py`)

None have subscribe commands; the frontend subscribes to `*_registry_updated` events and refetches.

### 5.1 `config/entity_registry/list_for_display` (no admin)
Result: `{"entity_categories": {"0":"config","1":"diagnostic"}, "entities":[...]}`
(`ENTITY_CATEGORY_INDEX_TO_VALUE = dict(enumerate(EntityCategory))`; JSON keys are strings).
Only entries with `disabled_by is None`. Per entity (`RegistryEntry._as_display_dict`,
`helpers/entity_registry.py`):
| key | meaning | presence |
|---|---|---|
| `ei` | entity_id | always |
| `pl` | platform (integration domain) | always |
| `ai` | area_id | if set |
| `lb` | labels list | always (set -> list, even empty) |
| `di` | device_id | if set |
| `np` | next_name_part: `"area"` if area_id set, else `"device"` if device_id set (`helpers/registry.NextNamePart`, also `parent_device`) | if not null |
| `ic` | user icon override | if set |
| `tk` | translation_key | if set |
| `ec` | entity_category index (0=config,1=diagnostic) | if set |
| `hb` | `true` if hidden_by set | if hidden |
| `hn` | `true` if has_entity_name | if true |
| `en` | name: user `name`, else `original_name_unprefixed`, else `original_name` | if not null |
| `dp` | sensor display precision (`options.sensor.display_precision` else `suggested_display_precision`) | sensor only |
(`DISPLAY_DICT_OPTIONAL` order: ai, lb, di, np, ic, tk.) With `hn` true, `en` is the entity part
only; friendly name = device name + " " + en (frontend logic). `config/entity_registry/list`
(full `as_partial_dict`) is larger; `config/entity_registry/get` / `get_entries` for details.
Event `entity_registry_updated`: `{"action":"create"|"remove","entity_id"}` or
`{"action":"update","entity_id","changes":{old values},"old_entity_id"?}`.

### 5.2 `config/device_registry/list` (no admin)
Array of `DeviceEntry.dict_repr` **and** `ChildDeviceEntry` items (`chain(registry.devices, registry.child_devices)`):
`area_id, configuration_url, config_entries[] (deprecated, rm 2027.8), config_entries_subentries,
config_entry_id, config_subentry_id, connections, created_at, disabled_by, entry_type, hw_version,
id, identifiers, labels, manufacturer, model, model_id, modified_at, name_by_user, name,
next_name_part, parent_device_id (null for normal devices), primary_config_entry (deprecated,
rm 2027.10), serial_number, sw_version, via_device_id`. Child devices carry a smaller set
(`area_id, config_entry_id, config_subentry_id, created_at, disabled_by, id, identifiers,
labels, modified_at, name_by_user, name, next_name_part, parent_device_id`) — new in this era;
parse leniently. Display name = `name_by_user ?: name`.
Event `device_registry_updated`: `{"action":"create","device_id"}`, `{"action":"remove","device_id","device":{...}}`,
`{"action":"update","device_id","changes":{...}}`.

### 5.3 `config/area_registry/list`, `config/floor_registry/list`, `config/label_registry/list` (no admin)
- Area (`AreaEntry.json_fragment`): `aliases, area_id, floor_id, humidity_entity_id, icon, labels,
  name, picture, temperature_entity_id, created_at, modified_at`. Event `area_registry_updated`
  `{"action":"create"|"remove"|"update"|"reorder","area_id":str|null}`.
- Floor (`floor_registry._entry_dict`): `aliases, created_at, floor_id, icon, level, name, modified_at`.
  Event `floor_registry_updated` `{"action":"create"|"remove"|"update","floor_id"}` or reorder variant.
- Timestamps are float epoch seconds.

## 6. `auth/current_user` (`components/auth/__init__.py websocket_current_user`)
Result: `{"id","name","is_owner","is_admin","credentials":[{"auth_provider_type","auth_provider_id"}],"mfa_modules":[{"id","name","enabled"}]}`.
Use `id` for dashboard `user` visibility conditions and `is_admin` for `require_admin`/
admin-only commands. Also useful: `auth/sign_path` `{path, expires=30}` -> `{"path": signed}`
for authenticated media URLs (camera/entity_picture).

## 7. Version gating
- Version strings: `auth_required`/`auth_ok` `ha_version` (earliest, no extra round-trip);
  `get_config.version`; frontend version via `frontend/get_version`.
- Probe-by-error is safe: unknown commands return `unknown_command` without closing the socket.
- Dated notes in source: legacy `lovelace: mode: yaml` breaks in `2026.8.0`; `lovelace/resources`
  bare alias "remove in 2025.1" (still present); device `config_entries` rm 2027.8,
  `primary_config_entry` rm 2027.10. No per-command "introduced in" markers exist in core code;
  minimum-version knowledge (e.g. `subscribe_entities`, `list_for_display`, `return_response`,
  `include/exclude` on subscribe_entities, floors/labels) must come from release notes.
  Defensive approach: treat missing keys as optional and fall back on `unknown_command`
  (e.g. subscribe_entities -> get_states + subscribe_events state_changed).

## 8. Subscription semantics summary
- Order: most subscriptions send result then events; **`frontend/subscribe_user_data` /
  `subscribe_system_data` send the first event before the result.**
- `subscribe_entities` filters: `entity_ids` list and/or include/exclude; new subscription per
  filter change (unsubscribe the old). Permissions enforced server-side; non-admins with
  restricted users get only readable entities.
- `subscribe_events` events are serialized once per event (`cached_event_message`), no per-client filtering except state_changed perms.
- On reconnect, all subscriptions are gone; re-auth, reset id counter (new connection), resubscribe, and re-fetch `lovelace/config` (no replay).

---

## Protocol cheat sheet (minimal client)

```jsonc
// <- server
{"type":"auth_required","ha_version":"2026.11.0"}
// -> client (within 10 s)
{"type":"auth","access_token":"eyJ..."}
// <- {"type":"auth_ok","ha_version":"2026.11.0"} | {"type":"auth_invalid","message":"..."}

// optional: batch outgoing messages into arrays
{"id":1,"type":"supported_features","features":{"coalesce_messages":1}}

{"id":2,"type":"auth/current_user"}            // -> result {id,name,is_owner,is_admin,...}
{"id":3,"type":"get_config"}                   // -> result {...,"version":"2026.11.0",...}

// list dashboards (sidebar-ready, admin-filtered) and/or raw metadata
{"id":4,"type":"get_panels"}                   // filter component_name=="lovelace"
{"id":5,"type":"lovelace/dashboards/list"}     // [{id,url_path,mode,title,icon?,show_in_sidebar,require_admin}]

// load config (omit url_path or null for default)
{"id":6,"type":"lovelace/config","url_path":null,"force":false}
// <- {"id":6,"type":"result","success":true,"result":{"views":[...]}}
// <- {"id":6,"type":"result","success":false,"error":{"code":"config_not_found","message":"No config found."}}  => auto-generate

// react to edits
{"id":7,"type":"subscribe_events","event_type":"lovelace_updated"}
// <- {"id":7,"type":"result","success":true,"result":null}
// <- {"id":7,"type":"event","event":{"event_type":"lovelace_updated","data":{"url_path":"my-dash"},"origin":"LOCAL","time_fired":"...","context":{...}}}
{"id":8,"type":"subscribe_events","event_type":"panels_updated"}   // dashboard list changes

// entity state
{"id":9,"type":"subscribe_entities","entity_ids":["light.kitchen","sensor.temp"]}
// <- {"id":9,"type":"result","success":true,"result":null}
// <- {"id":9,"type":"event","event":{"a":{"light.kitchen":{"s":"on","a":{"brightness":255},"c":"01J...","lc":1759900000.123}}}}
// <- {"id":9,"type":"event","event":{"c":{"light.kitchen":{"+":{"s":"off","lc":1759900100.5,"c":"01J...","a":{"brightness":null}}}}}}
// <- {"id":9,"type":"event","event":{"c":{"sensor.temp":{"+":{"lu":1759900200.0,"a":{"x":1}},"-":{"a":["old_attr"]}}}}}
// <- {"id":9,"type":"event","event":{"r":["light.kitchen"]}}

// act
{"id":10,"type":"call_service","domain":"light","service":"turn_on",
 "target":{"entity_id":"light.kitchen"},"service_data":{"brightness_pct":50}}
// <- {"id":10,"type":"result","success":true,"result":{"context":{"id":"01J...","parent_id":null,"user_id":"abc"}}}
{"id":11,"type":"call_service","domain":"weather","service":"get_forecasts",
 "target":{"entity_id":"weather.home"},"service_data":{"type":"daily"},"return_response":true}
// <- result {"context":{...},"response":{"weather.home":{"forecast":[...]}}}

{"id":12,"type":"ping"}                        // <- {"id":12,"type":"pong"}
{"id":13,"type":"unsubscribe_events","subscription":9}
```
