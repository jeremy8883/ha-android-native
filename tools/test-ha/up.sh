#!/usr/bin/env bash
# Start the local HA test instance (idempotent). Only ever talks to localhost.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAME=ha-native-test
IMAGE=ghcr.io/home-assistant/home-assistant:2026.7.4
PORT=8124
URL="http://localhost:${PORT}"
RUNTIME="$HERE/runtime"

if ! podman image exists "$IMAGE"; then
  podman pull "$IMAGE"
fi

# Fresh runtime dir: start from a clean copy of the template config.
if [[ ! -f "$RUNTIME/config/configuration.yaml" ]]; then
  mkdir -p "$RUNTIME"
  cp -r "$HERE/config" "$RUNTIME/config"
fi

if podman container exists "$NAME"; then
  if [[ "$(podman inspect -f '{{.State.Running}}' "$NAME")" != "true" ]]; then
    podman start "$NAME" >/dev/null
  fi
else
  # Bound to 127.0.0.1 only. The Android emulator reaches it as http://10.0.2.2:8124.
  podman run -d --name "$NAME" \
    -p "127.0.0.1:${PORT}:8123" \
    -e TZ=Europe/Amsterdam \
    -v "$RUNTIME/config:/config:Z" \
    "$IMAGE" >/dev/null
fi

echo -n "Waiting for Home Assistant at $URL "
for _ in $(seq 1 180); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$URL/manifest.json" || true)
  [[ "$code" == "200" ]] && break
  echo -n .
  sleep 1
done
echo
[[ "$code" == "200" ]] || { echo "HA did not become ready; see: podman logs $NAME" >&2; exit 1; }

# Bootstrap when not onboarded yet, or when the token file is missing.
# /api/onboarding returns the step list until onboarding has finished.
onboarding=$(curl -s "$URL/api/onboarding" || true)
if [[ ! -f "$HERE/.env" ]] || grep -q '"done":false\|"done": false' <<<"$onboarding"; then
  python3 "$HERE/bootstrap.py"
fi

echo "Home Assistant test instance: $URL  (login dev / dev; token in tools/test-ha/.env)"
