#!/usr/bin/env bash
# Remove the container and wipe all runtime state (.storage, db, logs, .env).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$HERE/down.sh"
# Files created inside the container may be owned by a subuid; unshare handles that.
podman unshare rm -rf "$HERE/runtime"
rm -f "$HERE/.env"
echo "runtime state wiped; run up.sh for a fresh instance"
