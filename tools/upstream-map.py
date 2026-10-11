#!/usr/bin/env python3
"""Links between the native dashboard's composables and the web frontend they port.

Every composable function in :dashboard and :dashboard-app says what it ports in its KDoc, on a line of its own:

    Upstream: `hui-tile-card` (src/panels/lovelace/cards/hui-tile-card.ts)
    Upstream: part of `ha-more-info-dialog` (src/dialogs/more-info/ha-more-info-dialog.ts)
    Upstream: `ha-date-input`, `ha-time-input` (src/components/ha-date-input.ts; src/components/ha-time-input.ts)
    Upstream: none (native: the app's own settings)

Paths are relative to the frontend's repository (the version tracked in progress.md). The link only goes this way:
the frontend is never changed.

Usage:
    tools/upstream-map.py check            composables without an Upstream line, and paths that don't exist
    tools/upstream-map.py map              each upstream file with the composables that port it
    tools/upstream-map.py changed < files  the composables to review for a list of changed upstream paths, such as
                                           `git diff --name-only 20260624.6 20260801.0 -- src` in the frontend
"""

import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRONTEND = os.environ.get("FRONTEND_SRC", os.path.expanduser("~/projects/ha-refs/frontend-20260624.6"))
SOURCES = ["dashboard/src/main/kotlin", "dashboard-app/src/main/kotlin"]

COMPOSABLE = re.compile(r"^\s*@Composable\s*$")
FUN = re.compile(r"^\s*(?:(?:private|internal|public|inline|override)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)\s*\(")
UPSTREAM = re.compile(r"Upstream:\s*(.*)")
PATHS = re.compile(r"\(([^()]*src/[^()]*)\)\s*$")


def composables():
    """Yields (file, line, name, upstream text or None) for each composable function outside previews."""
    for source in SOURCES:
        for directory, _, files in os.walk(os.path.join(ROOT, source)):
            for name in sorted(files):
                if name.endswith(".kt"):
                    yield from file_composables(os.path.join(directory, name))


def file_composables(path):
    lines = open(path, encoding="utf-8").read().split("\n")
    for index, line in enumerate(lines):
        if not COMPOSABLE.match(line):
            continue
        # The function follows its annotations
        fun_line = next((i for i in range(index + 1, min(index + 6, len(lines))) if FUN.match(lines[i])), None)
        if fun_line is None:
            continue
        annotations = lines[index - 3:fun_line]
        if any("@Preview" in a for a in annotations):
            continue
        yield path, fun_line + 1, FUN.match(lines[fun_line]).group(1), upstream_of(lines, index)


def upstream_of(lines, annotation):
    """The Upstream line of the KDoc (or comments) right above the annotations at [annotation]."""
    i = annotation - 1
    while i >= 0 and lines[i].strip().startswith("@"):
        i -= 1
    text = []
    while i >= 0 and (lines[i].strip().startswith(("*", "/**", "//", "*/")) or lines[i].strip() == "*"):
        text.append(lines[i])
        if lines[i].strip().startswith("/**"):
            break
        i -= 1
    text.reverse()
    for at, line in enumerate(text):
        match = UPSTREAM.search(line)
        if match:
            # A long line continues on the next comment lines, up to a blank line, a tag or the comment's end
            parts = [match.group(1)]
            for more in text[at + 1:]:
                content = more.strip().lstrip("/*").strip()
                if not content or content.startswith("@") or more.strip().startswith("*/"):
                    break
                parts.append(content)
            return " ".join(p.strip().removesuffix("*/").strip() for p in parts)
    return None


def upstream_paths(text):
    match = PATHS.search(text)
    if not match:
        return []
    return [p.strip() for p in match.group(1).split(";") if p.strip()]


def relative(path):
    return os.path.relpath(path, ROOT)


def check():
    problems = 0
    for path, line, name, upstream in composables():
        where = f"{relative(path)}:{line} {name}"
        if upstream is None:
            print(f"missing: {where}")
            problems += 1
        elif upstream.startswith("none"):
            continue
        else:
            paths = upstream_paths(upstream)
            if not paths:
                print(f"no path: {where}: {upstream}")
                problems += 1
            for p in paths:
                if not os.path.exists(os.path.join(FRONTEND, p.rstrip("/"))):
                    print(f"not found: {where}: {p}")
                    problems += 1
    print(f"{problems} problem(s)")
    return 1 if problems else 0


def index():
    by_path = defaultdict(list)
    for path, line, name, upstream in composables():
        for p in upstream_paths(upstream or ""):
            by_path[p].append(f"{relative(path)}:{line} {name}")
    return by_path


def show_map():
    for p, users in sorted(index().items()):
        print(p)
        for user in users:
            print(f"    {user}")
    return 0


def changed():
    by_path = index()
    changed_paths = [l.strip() for l in sys.stdin if l.strip()]
    for p, users in sorted(by_path.items()):
        # A directory link covers the files under it
        if any(c == p or (p.endswith("/") and c.startswith(p)) for c in changed_paths):
            print(p)
            for user in users:
                print(f"    {user}")
    return 0


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "check"
    sys.exit({"check": check, "map": show_map, "changed": changed}[command]())
