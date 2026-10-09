# Local Home Assistant test instance

A disposable HA **2026.7.4** in podman (`ha-native-test`) with the `demo` integration plus template
entities and `input_*` helpers. It has no real hardware and no secrets, so reads **and** writes
(service calls) are safe here. Use it for development and for recording fixtures.

- URL: <http://localhost:8124> (bound to 127.0.0.1; from the Android emulator use `http://10.0.2.2:8124`)
- Login: **dev / dev** (admin)
- Long-lived token: `tools/test-ha/.env` (`TEST_HA_URL`, `TEST_HA_TOKEN`, gitignored, written by `bootstrap.py`)

## Commands

```sh
tools/test-ha/up.sh       # start (idempotent); onboards + seeds on first run
tools/test-ha/down.sh     # stop and remove the container (keeps runtime state)
tools/test-ha/reset.sh    # remove the container and wipe runtime/ and .env
python3 tools/test-ha/bootstrap.py   # re-apply areas/floors/dashboard (idempotent)
```

The probe defaults to this instance:

```sh
python3 tools/ha-probe/ha_ro.py call get_states
python3 tools/ha-probe/ha_ro.py call lovelace/config '{"url_path":"dashboard-test"}'
python3 tools/ha-probe/ha_ro.py call call_service \
  '{"domain":"light","service":"toggle","target":{"entity_id":"light.kitchen_lights"}}' --allow-write
```

`--live` switches to `$HASS_SERVER`/`$HASS_TOKEN` (strictly read-only, and `--allow-write` is refused).

## Layout

- `config/` is the template config (committed). `up.sh` copies it to `runtime/config/` (gitignored) on
  first start. After changing `config/`, run `reset.sh && up.sh`.
- `bootstrap.py` does the following:
  - Onboarding over REST (`/api/onboarding/users` → `/auth/token` → `core_config`, `analytics`,
    `integration`).
  - Creates the long-lived token over WS.
  - Seeds 3 floors and 7 areas (one without a floor), and assigns demo devices and entities to areas.
  - Creates the storage dashboard `dashboard-test` (from `dashboard-test.json`).
  - Sets `frontend/set_system_data home` favorites.
  - On first run only, makes a few service calls so `usage_prediction/common_control` returns data.

## Quirks

- Some demo entities have no `unique_id`, so they're not in the entity registry and can't get an area:
  media players, locks, cameras, sirens, humidifiers, calendars and `air_quality`. The config adds
  `universal` media players (`media_player.*_tv` / `*_speaker`) and template locks
  (`lock.front_door_deadbolt`, `lock.back_door_lock`) that wrap them and can be placed in areas.
- Onboarding creates the areas Living Room, Kitchen and Bedroom itself. It also sets up `met`,
  `google_translate`, `radio_browser` and `shopping_list`, so the container makes some outbound calls
  (for example the met.no weather API).
- `usage_prediction/common_control` caches its result per user for 24h, and only counts the current
  time-of-day bucket of the seeded calls.
- Template sensor values partly derive from `now()` so they change over time.
