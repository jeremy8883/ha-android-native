#!/usr/bin/env python3
"""Extract the frontend translation subtrees used by ported dashboard code.

Usage: extract_frontend.py <frontend checkout> [languages...]
Writes dashboard-core/src/main/resources/translations/frontend-<lang>.json.
Only English exists in the frontend repo; other languages come from the
release build (home-assistant-frontend package) and are a later step.
"""

import json
import re
import sys
from pathlib import Path

# Dotted paths of the subtrees to keep. Extend when ported code needs more keys.
SUBTREES = [
    "panel",
    "state",
    "ui.notification_toast",
    "ui.card",
    "ui.panel.lovelace.cards.actions",
    "ui.panel.lovelace.components",
    "ui.panel.lovelace.editor.action-editor.actions",
    "ui.panel.energy",
    "ui.panel.lovelace.strategy",
    "ui.panel.page-authorize",
]

OUT_DIR = Path(__file__).resolve().parents[2] / "dashboard-core/src/main/resources/translations"


def pick(source: dict, path: str) -> dict:
    node = source
    for part in path.split("."):
        node = node[part]
    result: dict = {}
    target = result
    parts = path.split(".")
    for part in parts[:-1]:
        target = target.setdefault(part, {})
    target[parts[-1]] = node
    return result


def merge(into: dict, other: dict) -> None:
    for key, value in other.items():
        if isinstance(value, dict) and isinstance(into.get(key), dict):
            merge(into[key], value)
        else:
            into[key] = value


REFERENCE = re.compile(r"\[%key:([^%]+)%\]")


def resolve(value, source: dict, depth: int = 0):
    """Replace `[%key:a::b::c%]` references with the referenced string, as the frontend build does."""
    if isinstance(value, dict):
        return {key: resolve(item, source, depth) for key, item in value.items()}
    if not isinstance(value, str) or depth > 10:
        return value

    def lookup(match: re.Match) -> str:
        node = source
        for part in match.group(1).split("::"):
            node = node[part]
        return resolve(node, source, depth + 1)

    return REFERENCE.sub(lookup, value)


def main() -> None:
    frontend = Path(sys.argv[1])
    for language in sys.argv[2:] or ["en"]:
        source = json.loads((frontend / f"src/translations/{language}.json").read_text())
        subset: dict = {}
        for path in SUBTREES:
            merge(subset, resolve(pick(source, path), source))
        out = OUT_DIR / f"frontend-{language}.json"
        out.write_text(json.dumps(subset, indent=1, ensure_ascii=False, sort_keys=True) + "\n")
        print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
