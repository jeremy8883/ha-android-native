#!/usr/bin/env python3
"""Read-only Home Assistant WebSocket probe for development.

Reads $HASS_SERVER and $HASS_TOKEN from the environment and never prints them.
Only commands on READ_ONLY_COMMANDS are allowed; anything else is refused before
it reaches the server.

Usage:
  ha_ro.py call <type> [json-params] [--out FILE]
  ha_ro.py subscribe <type> [json-params] [--seconds N] [--out FILE]
"""

import argparse
import asyncio
import json
import os
import sys

import aiohttp

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


def websocket_url() -> str:
    server = os.environ.get("HASS_SERVER", "").rstrip("/")
    if not server:
        sys.exit("HASS_SERVER is not set")
    if server.startswith("https://"):
        server = "wss://" + server[len("https://"):]
    elif server.startswith("http://"):
        server = "ws://" + server[len("http://"):]
    return server + "/api/websocket"


async def run(args) -> None:
    if args.type not in READ_ONLY_COMMANDS:
        sys.exit(f"refused: '{args.type}' is not on the read-only allowlist")
    token = os.environ.get("HASS_TOKEN")
    if not token:
        sys.exit("HASS_TOKEN is not set")
    params = json.loads(args.params) if args.params else {}
    out = open(args.out, "w") if args.out else sys.stdout

    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(websocket_url(), max_msg_size=0) as ws:
            hello = await ws.receive_json()
            print(f"server ha_version={hello.get('ha_version')}", file=sys.stderr)
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
    asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    main()
