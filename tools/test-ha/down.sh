#!/usr/bin/env bash
# Stop and remove the test container. Runtime state in runtime/ is kept.
set -euo pipefail
podman rm -f ha-native-test >/dev/null 2>&1 || true
echo "ha-native-test removed"
