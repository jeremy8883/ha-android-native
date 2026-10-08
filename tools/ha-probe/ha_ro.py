#!/usr/bin/env python3
"""Home Assistant WebSocket probe for development.

Default (test mode): targets the local test instance (tools/test-ha, see its README).
Reads TEST_HA_URL / TEST_HA_TOKEN from tools/test-ha/.env; the URL must be localhost.
Read-only allowlist applies unless --allow-write is given, which permits any command.

--live: targets the real instance from $HASS_SERVER / $HASS_TOKEN. The read-only
allowlist is always enforced and --allow-write is refused.

Token values are never printed.

Usage:
  ha_ro.py call <type> [json-params] [--out FILE] [--live | --allow-write]
  ha_ro.py subscribe <type> [json-params] [--seconds N] [--out FILE] [--live | --allow-write]
"""

import argparse
import asyncio
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlparse

import aiohttp

TEST_ENV_FILE = Path(__file__).resolve().parent.parent / "test-ha" / ".env"
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}

READ_ONLY_COMMANDS = frozenset({
    "get_config",
    "get_states",
    "get_services",
    "get_panels",
    "auth/current_user",
    "lovelace/config",
    "lovelace/dashboards/list",
    "lovelace/resources",
    "config/entity_registry/list_for_display",
    "config/entity_registry/list",
    "config/device_registry/list",
    "config/area_registry/list",
    "config/floor_registry/list",
    "config/label_registry/list",
    "frontend/get_translations",
    "frontend/get_icons",
    "frontend/get_themes",
    "frontend/get_user_data",
    "frontend/get_system_data",
    "frontend/subscribe_user_data",
    "frontend/subscribe_system_data",
    "usage_prediction/common_control",
    "subscribe_entities",
    "subscribe_events",
    "render_template",
})


def read_env_file(path: Path) -> dict[str, str]:
    if not path.is_file():
        sys.exit(f"{path} not found; run tools/test-ha/up.sh first")
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def target(live: bool) -> tuple[str, str]:
    """Return (server url, token) for the selected mode."""
    if live:
        server, token = os.environ.get("HASS_SERVER", ""), os.environ.get("HASS_TOKEN", "")
        if not server or not token:
            sys.exit("--live needs HASS_SERVER and HASS_TOKEN")
        return server, token
    env = read_env_file(TEST_ENV_FILE)
    server, token = env.get("TEST_HA_URL", ""), env.get("TEST_HA_TOKEN", "")
    if not server or not token:
        sys.exit(f"TEST_HA_URL / TEST_HA_TOKEN missing in {TEST_ENV_FILE}")
    if urlparse(server).hostname not in LOCAL_HOSTS:
        sys.exit("refused: test mode only connects to localhost")
    return server, token


def websocket_url(server: str) -> str:
    server = server.rstrip("/")
    if server.startswith("https://"):
        server = "wss://" + server[len("https://"):]
    elif server.startswith("http://"):
        server = "ws://" + server[len("http://"):]
    return server + "/api/websocket"


async def run(args) -> None:
    if args.live and args.allow_write:
        sys.exit("refused: --allow-write is not permitted with --live")
    if args.type not in READ_ONLY_COMMANDS and not args.allow_write:
        sys.exit(f"refused: '{args.type}' is not on the read-only allowlist (test mode: pass --allow-write)")
    server, token = target(args.live)
    params = json.loads(args.params) if args.params else {}
    out = open(args.out, "w") if args.out else sys.stdout

    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(websocket_url(server), max_msg_size=0) as ws:
            hello = await ws.receive_json()
            mode = "live" if args.live else "test"
            print(f"[{mode}] server ha_version={hello.get('ha_version')}", file=sys.stderr)
            await ws.send_json({"type": "auth", "access_token": token})
            auth = await ws.receive_json()
            if auth.get("type") != "auth_ok":
                sys.exit(f"auth failed: {auth.get('type')}")

            await ws.send_json({"id": 1, "type": args.type, **params})
            if args.mode == "call":
                msg = await ws.receive_json()
                json.dump(msg, out, indent=2, ensure_ascii=False)
                out.write("\n")
                return

            messages = []
            try:
                async with asyncio.timeout(args.seconds):
                    async for raw in ws:
                        messages.append(json.loads(raw.data))
            except TimeoutError:
                pass
            json.dump(messages, out, indent=2, ensure_ascii=False)
            out.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["call", "subscribe"])
    parser.add_argument("type")
    parser.add_argument("params", nargs="?")
    parser.add_argument("--seconds", type=float, default=10)
    parser.add_argument("--out")
    parser.add_argument("--live", action="store_true", help="use $HASS_SERVER/$HASS_TOKEN (read-only)")
    parser.add_argument("--allow-write", action="store_true", help="test mode only: allow any command")
    asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    main()
