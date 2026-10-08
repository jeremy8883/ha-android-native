#!/usr/bin/env python3
"""Onboard and seed the local HA test instance (tools/test-ha). Idempotent.

1. Onboarding over REST (same flow as ludeeus/setup-homeassistant):
   POST /api/onboarding/users -> auth_code -> POST /auth/token -> core_config,
   analytics, integration. If the user step is already done, logs in via the
   normal /auth/login_flow instead.
2. Creates a long-lived token over WS and writes tools/test-ha/.env.
3. Seeds floors, areas, device/entity area assignments, a storage dashboard,
   home system data and some service-call history (for usage_prediction).

Only ever connects to localhost. Never prints token values.
"""

import argparse
import asyncio
import json
import sys
from pathlib import Path
from urllib.parse import urlparse

import aiohttp

HERE = Path(__file__).resolve().parent
ENV_FILE = HERE / ".env"
DASHBOARD_FILE = HERE / "dashboard-test.json"

USERNAME = "dev"
PASSWORD = "dev"
NAME = "Dev"
LLAT_CLIENT_NAME = "native-dashboard-test"


def log(msg: str) -> None:
    print(f"[bootstrap] {msg}", flush=True)


# --------------------------------------------------------------------------- REST


class Rest:
    def __init__(self, session: aiohttp.ClientSession, base: str) -> None:
        self.session = session
        self.base = base
        self.client_id = base + "/"

    async def json(self, method: str, path: str, token: str | None = None, **kwargs):
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        async with self.session.request(method, self.base + path, headers=headers, **kwargs) as resp:
            body = await resp.text()
            if resp.status >= 400:
                raise RuntimeError(f"{method} {path} -> HTTP {resp.status}: {body[:300]}")
            return json.loads(body) if body else {}

    async def onboarding_status(self) -> dict[str, bool] | None:
        """Step -> done, or None when the onboarding API is gone (fully onboarded)."""
        async with self.session.get(self.base + "/api/onboarding") as resp:
            if resp.status != 200:
                return None
            return {s["step"]: s["done"] for s in await resp.json()}

    async def token_from_code(self, code: str) -> str:
        data = {"grant_type": "authorization_code", "code": code, "client_id": self.client_id}
        tokens = await self.json("POST", "/auth/token", data=data)
        return tokens["access_token"]

    async def login(self) -> str:
        """Normal username/password login flow -> access token."""
        flow = await self.json("POST", "/auth/login_flow", json={
            "client_id": self.client_id,
            "handler": ["homeassistant", None],
            "redirect_uri": self.client_id,
        })
        result = await self.json("POST", f"/auth/login_flow/{flow['flow_id']}", json={
            "client_id": self.client_id, "username": USERNAME, "password": PASSWORD,
        })
        if result.get("type") != "create_entry":
            raise RuntimeError(f"login failed: {result.get('errors') or result.get('type')}")
        return await self.token_from_code(result["result"])

    async def onboard(self) -> str:
        """Run the remaining onboarding steps; return a short-lived access token."""
        status = await self.onboarding_status()
        if status is not None and not status.get("user"):
            log("onboarding: creating user dev/dev")
            res = await self.json("POST", "/api/onboarding/users", json={
                "client_id": self.client_id,
                "name": NAME,
                "username": USERNAME,
                "password": PASSWORD,
                "language": "en",
            })
            token = await self.token_from_code(res["auth_code"])
        else:
            token = await self.login()

        if status is not None:
            if not status.get("core_config"):
                log("onboarding: core_config")
                await self.json("POST", "/api/onboarding/core_config", token)
            if not status.get("analytics"):
                log("onboarding: analytics")
                await self.json("POST", "/api/onboarding/analytics", token)
            if not status.get("integration"):
                log("onboarding: integration")
                await self.json("POST", "/api/onboarding/integration", token, json={
                    "client_id": self.client_id, "redirect_uri": self.client_id,
                })
        return token

    async def token_valid(self, token: str) -> bool:
        async with self.session.get(self.base + "/api/", headers={"Authorization": f"Bearer {token}"}) as resp:
            return resp.status == 200


# --------------------------------------------------------------------------- WS


class WsError(RuntimeError):
    pass


class Ws:
    def __init__(self, ws: aiohttp.ClientWebSocketResponse) -> None:
        self.ws = ws
        self.next_id = 1

    @classmethod
    async def connect(cls, session: aiohttp.ClientSession, base: str, token: str) -> "Ws":
        ws = await session.ws_connect(base.replace("http://", "ws://") + "/api/websocket", max_msg_size=0)
        await ws.receive_json()  # auth_required
        await ws.send_json({"type": "auth", "access_token": token})
        if (await ws.receive_json()).get("type") != "auth_ok":
            raise WsError("websocket auth failed")
        return cls(ws)

    async def call(self, type_: str, **params):
        msg_id = self.next_id
        self.next_id += 1
        await self.ws.send_json({"id": msg_id, "type": type_, **params})
        while True:
            msg = await self.ws.receive_json()
            if msg.get("id") == msg_id and msg.get("type") == "result":
                if not msg["success"]:
                    raise WsError(f"{type_}: {msg['error']}")
                return msg["result"]


# --------------------------------------------------------------------------- seed data

FLOORS = [  # floor_id (derived from name by HA), name, level, icon
    ("ground_floor", "Ground floor", 0, "mdi:home-floor-0"),
    ("first_floor", "First floor", 1, "mdi:home-floor-1"),
    ("outside", "Outside", None, "mdi:tree"),
]

AREAS = [  # area_id (derived from name by HA), name, floor_id, icon
    ("living_room", "Living Room", "ground_floor", "mdi:sofa"),
    ("kitchen", "Kitchen", "ground_floor", "mdi:stove"),
    ("garage", "Garage", "ground_floor", "mdi:garage"),
    ("bedroom", "Bedroom", "first_floor", "mdi:bed"),
    ("office", "Office", "first_floor", "mdi:desk"),
    ("garden", "Garden", "outside", "mdi:flower"),
]

# Demo device name -> area. Devices not listed stay unassigned on purpose
# (Sun, Date/Time/Text, numbers, updates, most vacuums...) so "other devices"
# views have content too.
DEVICE_AREAS: dict[str, str] = {
    "Living Room RGBWW Lights": "living_room",
    "Ceiling Lights": "living_room",
    "Living Room Window": "living_room",
    "Hall Window": "living_room",
    "HeatPump": "living_room",
    "Decorative Lights": "living_room",
    "Carbon dioxide": "living_room",
    "Demo Living Room Bulb Update": "living_room",
    "Kitchen Lights": "kitchen",
    "Kitchen Window": "kitchen",
    "Carbon monoxide": "kitchen",
    "Bed Light": "bedroom",
    "Ecobee": "bedroom",
    "Thermostat": "bedroom",
    "Office RGBW Lights": "office",
    "Hvac": "office",
    "AC": "office",
    "Push": "office",
    "Garage Door": "garage",
    "Power consumption": "garage",
    "Total energy 1": "garage",
    "Total gas 1": "garage",
    "Basement Floor Wet": "garage",
    "Demo vacuum 0 ground floor": "garage",
    "Entrance Color + White Lights": "garden",
    "Outside Temperature": "garden",
    "Outside Humidity": "garden",
    "Movement Backyard": "garden",
    "Pergola Roof": "garden",
    "Front Garden": "garden",
    "Back Garden": "garden",
    "Orchard": "garden",
    "Trees": "garden",
}

# Entities without a device (must have a unique_id to be in the entity registry).
ENTITY_AREAS: dict[str, str] = {
    "alarm_control_panel.security": "living_room",
    "fan.living_room_fan": "living_room",
    "media_player.living_room_tv": "living_room",
    "lock.front_door_deadbolt": "living_room",
    "binary_sensor.front_door": "living_room",
    "sensor.living_room_temperature": "living_room",
    "sensor.living_room_humidity": "living_room",
    "media_player.kitchen_speaker": "kitchen",
    "lock.back_door_lock": "kitchen",
    "binary_sensor.kitchen_motion": "kitchen",
    "counter.coffee_cups": "kitchen",
    "fan.ceiling_fan": "bedroom",
    "media_player.bedroom_speaker": "bedroom",
    "binary_sensor.bedroom_window": "bedroom",
    "sensor.bedroom_temperature": "bedroom",
    "input_number.bedroom_brightness": "bedroom",
    "media_player.office_speaker": "office",
    "binary_sensor.office_occupancy": "office",
    "sensor.office_temperature": "office",
    "water_heater.demo_water_heater": "garage",
    "binary_sensor.garage_door_contact": "garage",
    "sensor.garage_battery": "garage",
    "timer.laundry": "garage",
    "switch.garden_irrigation": "garden",
    "binary_sensor.garden_leak": "garden",
    "sensor.garden_temperature": "garden",
}

# frontend/set_system_data key "home" (HomeFrontendSystemData in the frontend).
HOME_SYSTEM_DATA: dict = {
    "favorite_entities": ["light.living_room_rgbww_lights", "climate.heatpump", "lock.front_door_deadbolt"],
}

# Service calls made as the dev user so usage_prediction/common_control has data
# (it reads call_service events with the user's context from the recorder).
# Toggles come in pairs so the final state equals the demo default.
USAGE_CALLS: list[tuple[str, str, str]] = (
    [("light", "toggle", "light.kitchen_lights")] * 6
    + [("light", "toggle", "light.living_room_rgbww_lights")] * 4
    + [("switch", "toggle", "switch.decorative_lights")] * 4
    + [("fan", "toggle", "fan.living_room_fan")] * 2
    + [("cover", "toggle", "cover.kitchen_window")] * 2
    + [("input_boolean", "toggle", "input_boolean.guest_mode")] * 2
)


async def wait_for_demo(ws: Ws) -> list[dict]:
    for _ in range(120):
        states = await ws.call("get_states")
        if any(s["entity_id"].startswith("light.") for s in states) and any(
            s["entity_id"].startswith("media_player.") for s in states
        ):
            return states
        await asyncio.sleep(1)
    raise RuntimeError("demo entities did not appear")


async def ensure_llat(ws: Ws) -> str:
    """Create a fresh long-lived token (replacing an older one with the same name)."""
    for rt in await ws.call("auth/refresh_tokens"):
        if rt.get("type") == "long_lived_access_token" and rt.get("client_name") == LLAT_CLIENT_NAME:
            await ws.call("auth/delete_refresh_token", refresh_token_id=rt["id"])
    return await ws.call("auth/long_lived_access_token", client_name=LLAT_CLIENT_NAME, lifespan=3650)


async def seed_structure(ws: Ws) -> None:
    floors = {f["floor_id"] for f in await ws.call("config/floor_registry/list")}
    for floor_id, name, level, icon in FLOORS:
        if floor_id not in floors:
            created = await ws.call("config/floor_registry/create", name=name, level=level, icon=icon)
            assert created["floor_id"] == floor_id, created
            log(f"floor created: {floor_id}")

    areas = {a["area_id"]: a for a in await ws.call("config/area_registry/list")}
    for area_id, name, floor_id, icon in AREAS:
        if area_id not in areas:
            created = await ws.call("config/area_registry/create", name=name, floor_id=floor_id, icon=icon)
            assert created["area_id"] == area_id, created
            log(f"area created: {area_id}")
        elif areas[area_id].get("floor_id") != floor_id or areas[area_id].get("icon") != icon:
            await ws.call("config/area_registry/update", area_id=area_id, floor_id=floor_id, icon=icon)
            log(f"area updated: {area_id}")

    devices = await ws.call("config/device_registry/list")
    for dev in devices:
        name = dev.get("name_by_user") or dev.get("name")
        area_id = DEVICE_AREAS.get(name)
        if area_id and dev.get("area_id") != area_id:
            await ws.call("config/device_registry/update", device_id=dev["id"], area_id=area_id)
    entities = {e["entity_id"]: e for e in await ws.call("config/entity_registry/list")}
    for entity_id, area_id in ENTITY_AREAS.items():
        entry = entities.get(entity_id)
        if entry is None:
            log(f"warning: {entity_id} not in entity registry, cannot assign area")
        elif entry.get("area_id") != area_id:
            await ws.call("config/entity_registry/update", entity_id=entity_id, area_id=area_id)


async def seed_dashboard(ws: Ws) -> None:
    config = json.loads(DASHBOARD_FILE.read_text())
    dashboards = {d["url_path"] for d in await ws.call("lovelace/dashboards/list")}
    if "dashboard-test" not in dashboards:
        await ws.call(
            "lovelace/dashboards/create",
            url_path="dashboard-test",
            title="Test dashboard",
            icon="mdi:test-tube",
            show_in_sidebar=True,
            require_admin=False,
            mode="storage",
        )
        log("dashboard created: dashboard-test")
    await ws.call("lovelace/config/save", url_path="dashboard-test", config=config)


async def seed_usage(ws: Ws, states: list[dict]) -> None:
    await ws.call("frontend/set_system_data", key="home", value=HOME_SYSTEM_DATA)
    existing = {s["entity_id"] for s in states}
    for domain, service, entity_id in USAGE_CALLS:
        if entity_id in existing:
            # service_data.entity_id (not target) is what usage_prediction reads.
            await ws.call("call_service", domain=domain, service=service, service_data={"entity_id": entity_id})
    log(f"made {len(USAGE_CALLS)} service calls for usage_prediction history")
    # common_control caches its result for 24h per user; let the recorder commit
    # (commit_interval 5s) before anything asks for it.
    await asyncio.sleep(6)


# --------------------------------------------------------------------------- main


async def main(base: str, skip_usage: bool) -> None:
    if urlparse(base).hostname not in {"localhost", "127.0.0.1", "::1"}:
        sys.exit("refused: bootstrap only targets localhost")

    async with aiohttp.ClientSession() as session:
        rest = Rest(session, base)
        env_token = None
        if ENV_FILE.is_file():
            for line in ENV_FILE.read_text().splitlines():
                if line.startswith("TEST_HA_TOKEN="):
                    env_token = line.split("=", 1)[1].strip()

        status = await rest.onboarding_status()
        onboarded = status is None or all(status.values())
        if onboarded and env_token and await rest.token_valid(env_token):
            log("already onboarded, reusing token from .env")
            token = env_token
            new_llat = False
        else:
            token = await rest.onboard()
            new_llat = True

        ws = await Ws.connect(session, base, token)
        if new_llat:
            llat = await ensure_llat(ws)
            ENV_FILE.write_text(f"TEST_HA_URL={base}\nTEST_HA_TOKEN={llat}\n")
            ENV_FILE.chmod(0o600)
            log(f"wrote {ENV_FILE.relative_to(HERE.parent.parent)}")

        states = await wait_for_demo(ws)
        await seed_structure(ws)
        await seed_dashboard(ws)
        # Usage history accumulates, so only seed it on a fresh instance.
        if not onboarded and not skip_usage:
            await seed_usage(ws, states)
        elif HOME_SYSTEM_DATA:
            await ws.call("frontend/set_system_data", key="home", value=HOME_SYSTEM_DATA)
        await ws.ws.close()
        log("done")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8124")
    parser.add_argument("--skip-usage", action="store_true", help="don't seed service-call history (only seeded on first onboarding anyway)")
    args = parser.parse_args()
    asyncio.run(main(args.url.rstrip("/"), args.skip_usage))
